package com.personal.chama.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.chama.common.Money;
import com.personal.chama.ledger.LedgerEntry;
import com.personal.chama.ledger.LedgerEntryType;
import com.personal.chama.ledger.LedgerService;
import com.personal.chama.member.Member;
import com.personal.chama.member.MemberRepository;

/**
 * Recovering what's owed on a loan (policy section 4, decisions R2-R7), as one confirmed
 * step: first the borrower's own shares, then the guarantors' shares (split equally, each
 * covering the other if one runs short), and anything still left is a pool loss for the
 * group to decide.
 *
 * Taking from shares lowers those shares and pays the loan by the same amount. No cash
 * moves, so the pool's cash balance doesn't change (R5); the loan stops being "out".
 */
@Service
@Transactional(readOnly = true)
public class RecoveryService {

	private final LoanRepository loans;
	private final GuaranteeRepository guarantees;
	private final RepaymentRepository repayments;
	private final GuarantorDebtRepository debts;
	private final MemberRepository members;
	private final LoanHistory history;
	private final LoanService loanService;
	private final LatePaymentService latePayments;
	private final LedgerService ledger;

	public RecoveryService(LoanRepository loans, GuaranteeRepository guarantees, RepaymentRepository repayments,
			GuarantorDebtRepository debts, MemberRepository members, LoanHistory history, LoanService loanService,
			LatePaymentService latePayments, LedgerService ledger) {
		this.loans = loans;
		this.guarantees = guarantees;
		this.repayments = repayments;
		this.debts = debts;
		this.members = members;
		this.history = history;
		this.loanService = loanService;
		this.latePayments = latePayments;
		this.ledger = ledger;
	}

	/** What would be recovered, and from whom, without changing anything. */
	public Plan plan(Long loanId) {
		Loan loan = loan(loanId);
		RepaymentSchedule schedule = history.schedule(loan);
		if (!history.recoveryAvailable(loan, schedule)) {
			throw new LoanException("There is nothing to recover on this loan right now.");
		}
		BigDecimal due = history.dueNow(loan, schedule);

		Member borrower = loan.getBorrower();
		BigDecimal borrowerShares = ledger.sharesOf(borrower.getId()).max(Money.ZERO);
		BigDecimal fromBorrower = due.min(borrowerShares);
		List<Part> parts = new ArrayList<>();
		parts.add(new Part(Repayment.Source.BORROWER_SHARES, borrower, borrowerShares, fromBorrower));

		BigDecimal rest = due.subtract(fromBorrower);
		List<Member> backers = guarantees.findByLoanId(loanId).stream().map(Guarantee::getGuarantor).toList();
		List<Part> guarantorParts = splitBetweenGuarantors(rest, backers);
		parts.addAll(guarantorParts);
		BigDecimal fromGuarantors = guarantorParts.stream().map(Part::take).reduce(Money.ZERO, BigDecimal::add);

		return new Plan(loan, due, parts, rest.subtract(fromGuarantors));
	}

	/**
	 * Equal shares of the shortfall; if one guarantor can't cover their part, the others
	 * cover the rest up to their own shares (Phase 4 plan, Q3).
	 */
	private List<Part> splitBetweenGuarantors(BigDecimal shortfall, List<Member> backers) {
		List<Part> parts = new ArrayList<>();
		if (backers.isEmpty()) {
			return parts;
		}
		int n = backers.size();
		BigDecimal each = shortfall.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
		BigDecimal[] shares = new BigDecimal[n];
		BigDecimal[] take = new BigDecimal[n];
		BigDecimal left = shortfall;
		for (int i = 0; i < n; i++) {
			shares[i] = ledger.sharesOf(backers.get(i).getId()).max(Money.ZERO);
			// The first guarantor's part absorbs any cent left over by the division.
			BigDecimal target = i == 0 ? shortfall.subtract(each.multiply(BigDecimal.valueOf(n - 1))) : each;
			take[i] = target.min(shares[i]);
			left = left.subtract(take[i]);
		}
		for (int i = 0; i < n && left.signum() > 0; i++) {
			BigDecimal extra = left.min(shares[i].subtract(take[i]));
			take[i] = take[i].add(extra);
			left = left.subtract(extra);
		}
		for (int i = 0; i < n; i++) {
			parts.add(new Part(Repayment.Source.GUARANTOR_SHARES, backers.get(i), shares[i], Money.orZero(take[i])));
		}
		return parts;
	}

	@Transactional
	public Plan recover(Long loanId) {
		Loan loan = loan(loanId);
		members.findByIdForUpdate(loan.getBorrower().getId());
		latePayments.reconcile(loan);
		Plan plan = plan(loanId);
		LocalDate today = history.today();
		if (today.isBefore(history.latestEventDate(loan))) {
			throw new LoanException("This loan has a payment or change dated after today.");
		}

		for (Part part : plan.parts()) {
			if (part.take().signum() <= 0) {
				continue;
			}
			RepaymentSchedule.Allocation allocation = history.schedule(loan).apply(part.take(), today);
			Long guarantorId = part.source() == Repayment.Source.GUARANTOR_SHARES ? part.member().getId() : null;
			Repayment repayment = repayments.save(Repayment.recovery(loan, part.take(), today, part.source(), guarantorId));
			LedgerEntryType shareType = part.source() == Repayment.Source.BORROWER_SHARES
					? LedgerEntryType.RECOVERY_FROM_SHARES : LedgerEntryType.GUARANTOR_CALL;
			LedgerEntry shareEntry = LedgerEntry.forLoan(shareType, part.take().negate(), part.member(), today,
					loan.getId(), repayment.getId(), "For " + loan.getBorrower().getFullName() + "'s loan");
			ledger.post(shareEntry);
			loanService.postAllocation(loan, allocation, today, repayment.getId());
			if (guarantorId != null) {
				debts.save(new GuarantorDebt(loan, part.member(), repayment.getId(), part.take()));
			}
		}
		if (plan.poolLoss().signum() > 0) {
			loan.flagPoolLoss();
		}
		loanService.afterPayment(loan);
		return plan;
	}

	private Loan loan(Long id) {
		return loans.findWithBorrowerById(id).orElseThrow(() -> new LoanException("No such loan."));
	}

	/** One source of recovery: whose shares, how much they have, and how much is taken. */
	public record Part(Repayment.Source source, Member member, BigDecimal shares, BigDecimal take) {
	}

	public record Plan(Loan loan, BigDecimal due, List<Part> parts, BigDecimal poolLoss) {

		public BigDecimal recovered() {
			return parts.stream().map(Part::take).reduce(Money.ZERO, BigDecimal::add);
		}
	}
}

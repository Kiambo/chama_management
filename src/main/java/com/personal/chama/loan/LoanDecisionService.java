package com.personal.chama.loan;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.chama.common.Money;
import com.personal.chama.decision.DecisionInput;
import com.personal.chama.decision.DecisionService;
import com.personal.chama.decision.GroupDecision;
import com.personal.chama.ledger.LedgerEntry;
import com.personal.chama.ledger.LedgerEntryRepository;
import com.personal.chama.ledger.LedgerEntryType;
import com.personal.chama.ledger.LedgerService;
import com.personal.chama.reference.PaymentReference;
import com.personal.chama.reference.ReferenceService;

/**
 * Changes to a loan that need a group decision (G1): waiving a late fee, extending,
 * changing the schedule, restructuring and writing off. Also repaying a guarantor.
 */
@Service
@Transactional(readOnly = true)
public class LoanDecisionService {

	private final LoanRepository loans;
	private final LateFeeRepository fees;
	private final ScheduleChangeRepository changes;
	private final InstallmentRepository installments;
	private final GuarantorDebtRepository debts;
	private final GuarantorDebtPaymentRepository debtPayments;
	private final LoanHistory history;
	private final LoanService loanService;
	private final LatePaymentService latePayments;
	private final DecisionService decisions;
	private final LoanCalculator calculator;
	private final LedgerService ledger;
	private final LedgerEntryRepository ledgerEntries;
	private final AuditorAware<String> auditor;
	private final Clock clock;
	private final ReferenceService references;

	public LoanDecisionService(LoanRepository loans, LateFeeRepository fees, ScheduleChangeRepository changes,
			InstallmentRepository installments, GuarantorDebtRepository debts,
			GuarantorDebtPaymentRepository debtPayments, LoanHistory history, LoanService loanService,
			LatePaymentService latePayments, DecisionService decisions, LoanCalculator calculator,
			LedgerService ledger, LedgerEntryRepository ledgerEntries, AuditorAware<String> auditor, Clock clock,
			ReferenceService references) {
		this.loans = loans;
		this.fees = fees;
		this.changes = changes;
		this.installments = installments;
		this.debts = debts;
		this.debtPayments = debtPayments;
		this.history = history;
		this.loanService = loanService;
		this.latePayments = latePayments;
		this.decisions = decisions;
		this.calculator = calculator;
		this.ledger = ledger;
		this.ledgerEntries = ledgerEntries;
		this.auditor = auditor;
		this.clock = clock;
		this.references = references;
	}

	// ---------------------------------------------------------------- fee waiver (R8)

	@Transactional
	public void waiveFee(Long loanId, Long feeId, DecisionInput input) {
		Loan loan = openLoan(loanId);
		LateFee fee = fees.findById(feeId).filter(f -> f.getLoanId().equals(loanId) && !f.isCancelled())
				.orElseThrow(() -> new LoanException("No such late fee on this loan."));
		BigDecimal unpaid = unpaidPartOf(loan, fee);
		if (unpaid.signum() <= 0) {
			throw new LoanException("Nothing is owed on this fee.");
		}
		GroupDecision decision = decisions.record(GroupDecision.Kind.FEE_WAIVER,
				"Late fee of KES " + Money.format(unpaid) + " waived on " + loan.getBorrower().getFullName() + "'s loan",
				input, loanId);
		waive(loan, fee, unpaid, decision.getId());
	}

	private BigDecimal unpaidPartOf(Loan loan, LateFee fee) {
		RepaymentSchedule.Line line = history.schedule(loan).lineFor(fee.getInstallmentId());
		BigDecimal paid = line == null ? Money.ZERO : line.feePaid();
		return fee.payable().subtract(paid).max(Money.ZERO);
	}

	private void waive(Loan loan, LateFee fee, BigDecimal unpaid, Long decisionId) {
		fee.waive(unpaid, decisionId);
		ledger.post(LedgerEntry.forLateFee(LedgerEntryType.LATE_FEE_WAIVED, unpaid.negate(), loan.getBorrower(),
				history.today(), loan.getId(), fee.getId(), "Waived by group decision"));
	}

	/**
	 * Unpaid late fees can't carry over to a new plan as fees, so they become part of it
	 * as an extra charge. Returns the amount moved.
	 */
	private BigDecimal foldUnpaidFeesIntoPlan(Loan loan, Long decisionId) {
		BigDecimal moved = Money.ZERO;
		for (LateFee fee : history.fees(loan.getId())) {
			BigDecimal unpaid = unpaidPartOf(loan, fee);
			if (unpaid.signum() > 0) {
				waive(loan, fee, unpaid, decisionId);
				moved = moved.add(unpaid);
			}
		}
		return moved;
	}

	// ---------------------------------------------------------------- extension (brief 4.4)

	/** What an extension would cost, for the form. */
	public BigDecimal extraInterestFor(Loan loan, Tenure newTenure) {
		return LoanCalculator.interest(loan.getPrincipal(), calculator.ratePercent(newTenure))
				.subtract(loan.getInterest()).max(Money.ZERO);
	}

	/**
	 * A longer tenure: the fee moves to the tier for the new total tenure, and what's left
	 * plus the extra interest is spread over the months up to the new end date (Q5).
	 */
	@Transactional
	public void extend(Long loanId, Tenure newTenure, DecisionInput input) {
		Loan loan = openLoan(loanId);
		requireNotDefaulted(loan);
		if (newTenure == null || newTenure.ordinal() <= loan.getTenure().ordinal()) {
			throw new LoanException("Choose a tenure longer than the current " + loan.getTenure().getLabel() + ".");
		}
		LocalDate today = history.today();
		List<LocalDate> dates = newTenure.dueDates(loan.getDisbursedDate()).stream().filter(d -> d.isAfter(today)).toList();
		if (dates.isEmpty()) {
			throw new LoanException("The new end date has already passed. Change the schedule instead.");
		}
		BigDecimal newRate = calculator.ratePercent(newTenure);
		BigDecimal extra = extraInterestFor(loan, newTenure);
		GroupDecision decision = decisions.record(GroupDecision.Kind.EXTENSION,
				loan.getBorrower().getFullName() + "'s loan extended from " + loan.getTenure().getLabel() + " at "
						+ loan.getRatePercent().stripTrailingZeros().toPlainString() + "% to " + newTenure.getLabel() + " at "
						+ newRate.stripTrailingZeros().toPlainString() + "% (+KES " + Money.format(extra) + " interest)",
				input, loanId);
		BigDecimal folded = foldUnpaidFeesIntoPlan(loan, decision.getId());
		loan.extend(newTenure, newRate);
		loan.addCharge(folded);
		newPlan(loan, decision.getId(), extra.add(folded), dates);
	}

	// ---------------------------------------------------------------- schedule change / restructure

	/**
	 * A new plan for what's left: {@code count} monthly installments from {@code firstDue}.
	 * A restructure (e.g. after a pool loss) may add an agreed extra charge (Q6) and marks
	 * the loan RESTRUCTURED; a plain schedule change (R9) only moves the dates.
	 */
	@Transactional
	public void changeSchedule(Long loanId, LocalDate firstDue, int count, BigDecimal extraCharge, boolean restructure,
			DecisionInput input) {
		Loan loan = openLoan(loanId);
		if (!restructure) {
			requireNotDefaulted(loan);
		}
		LocalDate today = history.today();
		if (firstDue == null || !firstDue.isAfter(today)) {
			throw new LoanException("The first new payment must be after today.");
		}
		if (count < 1 || count > 12) {
			throw new LoanException("Choose between 1 and 12 installments.");
		}
		BigDecimal extra = Money.orZero(extraCharge == null ? BigDecimal.ZERO : extraCharge);
		if (extra.signum() < 0 || (!restructure && extra.signum() > 0)) {
			throw new LoanException("An extra charge can only be added when restructuring, and can't be negative.");
		}
		List<LocalDate> dates = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			dates.add(firstDue.plusMonths(i));
		}
		GroupDecision decision = decisions.record(
				restructure ? GroupDecision.Kind.RESTRUCTURE : GroupDecision.Kind.SCHEDULE_CHANGE,
				(restructure ? "Restructured: " : "Schedule changed: ") + loan.getBorrower().getFullName() + "'s loan in "
						+ count + (count == 1 ? " payment" : " monthly payments") + " from " + firstDue
						+ (extra.signum() > 0 ? " (+KES " + Money.format(extra) + " agreed charge)" : ""),
				input, loanId);
		BigDecimal folded = foldUnpaidFeesIntoPlan(loan, decision.getId());
		if (restructure) {
			loan.restructure(extra);
		}
		loan.addCharge(folded);
		newPlan(loan, decision.getId(), extra.add(folded), dates);
	}

	/**
	 * Replaces the unpaid installments with new ones from today. The old ones are kept,
	 * marked superseded. What's left of the interest (plus any extra) and principal is
	 * spread over the new dates, remainder on the last.
	 */
	private void newPlan(Loan loan, Long decisionId, BigDecimal extraInterest, List<LocalDate> dates) {
		LocalDate today = history.today();
		if (today.isBefore(history.latestEventDate(loan))) {
			throw new LoanException("This loan has a payment or change dated after today.");
		}
		RepaymentSchedule schedule = history.schedule(loan);
		BigDecimal interestLeft = schedule.interestOwed().add(extraInterest);
		BigDecimal principalLeft = schedule.principalOwed();
		if (interestLeft.add(principalLeft).signum() <= 0) {
			throw new LoanException("Nothing is left to repay on this loan.");
		}
		ScheduleChange change = changes.save(new ScheduleChange(loan.getId(), decisionId, today, extraInterest));
		for (RepaymentSchedule.Line line : schedule.activeLines()) {
			if (!line.isPaid()) {
				line.installment().supersede(change.getId());
			}
		}
		List<LoanCalculator.PlannedInstallment> plan = LoanCalculator.split(principalLeft, interestLeft, dates);
		if (plan.stream().anyMatch(p -> p.amount().signum() <= 0)) {
			throw new LoanException("What's left is too small to split into " + dates.size() + " installments.");
		}
		for (LoanCalculator.PlannedInstallment planned : plan) {
			installments.save(new Installment(loan, planned, change.getId()));
		}
		latePayments.reconcile(loan);
	}

	private void requireNotDefaulted(Loan loan) {
		if (loan.getStatus() == LoanStatus.DEFAULTED) {
			throw new LoanException("This loan has defaulted. Recover what's owed, then restructure or write off.");
		}
	}

	// ---------------------------------------------------------------- write-off (R7, Q4)

	/** The principal that would be written off, and whether the group money covers it. */
	public WriteOff writeOffPreview(Long loanId) {
		Loan loan = openLoan(loanId);
		BigDecimal principal = ledger.principalOutstandingFor(loanId);
		BigDecimal groupMoney = ledger.groupMoney();
		return new WriteOff(loan, principal, history.schedule(loan).remaining(), groupMoney,
				principal.compareTo(groupMoney) > 0);
	}

	@Transactional
	public void writeOff(Long loanId, DecisionInput input) {
		Loan loan = openLoan(loanId);
		if (loan.getStatus() != LoanStatus.DEFAULTED) {
			throw new LoanException("Only a defaulted loan can be written off. Try recovery first.");
		}
		BigDecimal principal = ledger.principalOutstandingFor(loanId);
		GroupDecision decision = decisions.record(GroupDecision.Kind.WRITE_OFF,
				loan.getBorrower().getFullName() + "'s loan written off: KES " + Money.format(principal)
						+ " principal lost to the pool",
				input, loanId);
		for (LateFee fee : history.fees(loanId)) {
			BigDecimal unpaid = unpaidPartOf(loan, fee);
			if (unpaid.signum() > 0) {
				waive(loan, fee, unpaid, decision.getId());
			}
		}
		if (principal.signum() > 0) {
			ledger.post(LedgerEntry.forLoan(LedgerEntryType.LOAN_WRITTEN_OFF, principal, loan.getBorrower(),
					history.today(), loanId, null, "Written off by group decision"));
		}
		loan.writeOff();
	}

	// ---------------------------------------------------------------- repaying a guarantor (R6)

	@Transactional
	public void repayGuarantor(Long debtId, BigDecimal amount, LocalDate paidDate, String method, String reference) {
		GuarantorDebt debt = debts.findById(debtId).orElseThrow(() -> new LoanException("No such debt."));
		LoanService.DebtView view = loanService.debtView(debt);
		if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
			throw new LoanException("Enter an amount more than zero, with at most 2 decimal places.");
		}
		if (amount.compareTo(view.owed()) > 0) {
			throw new LoanException("That is more than is owed to " + debt.getGuarantor().getFullName() + " (KES "
					+ Money.format(view.owed()) + ").");
		}
		if (paidDate == null || paidDate.isAfter(history.today())) {
			throw new LoanException("The payment date can't be in the future.");
		}
		if (method == null || method.isBlank()) {
			throw new LoanException("Enter how it was paid.");
		}
		references.checkAvailable(reference, LoanException::new);
		GuarantorDebtPayment payment = debtPayments.save(new GuarantorDebtPayment(debtId, Money.orZero(amount), paidDate,
				method.strip(), reference == null || reference.isBlank() ? null : reference.strip()));
		references.claim(reference, PaymentReference.Kind.GUARANTOR_REPAYMENT, payment.getId(),
				"Repayment from " + debt.getLoan().getBorrower().getFullName() + " to " + debt.getGuarantor().getFullName(),
				LoanException::new);
		ledger.post(LedgerEntry.forDebtPayment(Money.orZero(amount), debt.getGuarantor(), paidDate,
				debt.getLoan().getId(), payment.getId(), "From " + debt.getLoan().getBorrower().getFullName()));
	}

	@Transactional
	public void voidGuarantorRepayment(Long debtId, Long paymentId, String reason) {
		GuarantorDebt debt = debts.findById(debtId).orElseThrow(() -> new LoanException("No such debt."));
		if (!paymentId.equals(loanService.debtView(debt).correctablePaymentId())) {
			throw new LoanException("Only the most recent repayment to a guarantor can be corrected.");
		}
		GuarantorDebtPayment payment = debtPayments.findById(paymentId).orElseThrow();
		for (LedgerEntry entry : ledgerEntries.findByDebtPaymentIdAndReversesIdIsNull(paymentId)) {
			if (!ledger.isReversed(entry.getId())) {
				ledger.reverse(entry.getId(), reason);
			}
		}
		payment.voidWith(reason, auditor.getCurrentAuditor().orElse("system"), Instant.now(clock));
		references.release(PaymentReference.Kind.GUARANTOR_REPAYMENT, paymentId);
	}

	private Loan openLoan(Long id) {
		Loan loan = loans.findWithBorrowerById(id).orElseThrow(() -> new LoanException("No such loan."));
		if (!loan.isActive()) {
			throw new LoanException("This loan is closed.");
		}
		return loan;
	}

	public record WriteOff(Loan loan, BigDecimal principal, BigDecimal totalOwed, BigDecimal groupMoney,
			boolean exceedsGroupMoney) {

		/** Interest and fees that will no longer be collected. */
		public BigDecimal forgone() {
			return totalOwed.subtract(principal).max(Money.ZERO);
		}
	}
}

package com.personal.chama.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.chama.config.ChamaProperties;
import com.personal.chama.decision.GroupDecision;
import com.personal.chama.decision.GroupDecisionRepository;
import com.personal.chama.ledger.LedgerEntry;
import com.personal.chama.ledger.LedgerEntryRepository;
import com.personal.chama.ledger.LedgerEntryType;
import com.personal.chama.ledger.LedgerService;

/**
 * Brings a loan up to date with its missed payments (decisions R1, R3, R4 and the Phase 4
 * plan): one late fee per missed installment, and DEFAULTED after two misses in a row.
 * Safe to run any number of times - it only adds what's missing and removes what turned
 * out to be wrong (e.g. a fee on an installment that a back-dated repayment shows was paid
 * in time).
 */
@Service
public class LatePaymentService {

	private static final Logger log = LoggerFactory.getLogger(LatePaymentService.class);

	private final LoanRepository loans;
	private final LateFeeRepository fees;
	private final LoanHistory history;
	private final LedgerService ledger;
	private final LedgerEntryRepository ledgerEntries;
	private final GroupDecisionRepository decisions;

	public LatePaymentService(LoanRepository loans, LateFeeRepository fees, LoanHistory history, LedgerService ledger,
			LedgerEntryRepository ledgerEntries, GroupDecisionRepository decisions) {
		this.loans = loans;
		this.fees = fees;
		this.history = history;
		this.ledger = ledger;
		this.ledgerEntries = ledgerEntries;
		this.decisions = decisions;
	}

	/** The late fee: the higher of the minimum or a % of the installment, in whole KES. */
	public BigDecimal feeFor(Installment installment) {
		ChamaProperties.Loan rules = history.settingsAsOf(history.deadline(installment).plusDays(1));
		BigDecimal percent = installment.getAmount().multiply(rules.lateFeePercent())
				.divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
		return percent.max(rules.lateFeeMinimum()).setScale(2);
	}

	@Transactional
	public void reconcileAll() {
		List<Loan> open = loans.findByStatusInOrderByDisbursedDateAsc(LoanStatus.OPEN);
		open.forEach(this::reconcile);
		if (!open.isEmpty()) {
			log.info("Checked {} open loans for missed payments", open.size());
		}
	}

	@Transactional
	public void reconcile(Long loanId) {
		loans.findWithBorrowerById(loanId).ifPresent(this::reconcile);
	}

	@Transactional
	public void reconcile(Loan loan) {
		if (!loan.isActive()) {
			return;
		}
		RepaymentSchedule schedule = history.schedule(loan);
		List<Installment> missed = history.missed(loan, schedule);

		// Two misses in a row: the whole balance is due from the day after the second deadline.
		LocalDate defaultOn = null;
		List<Installment> current = schedule.activeLines().stream().map(RepaymentSchedule.Line::installment).toList();
		for (int i = 1; i < current.size() && defaultOn == null; i++) {
			if (missed.contains(current.get(i - 1)) && missed.contains(current.get(i))) {
				defaultOn = history.deadline(current.get(i)).plusDays(1);
			}
		}

		// One fee per missed installment; none for misses after the loan has defaulted (Q2).
		Set<Long> shouldHaveFee = new HashSet<>();
		for (Installment i : missed) {
			LocalDate chargeOn = history.deadline(i).plusDays(1);
			if (defaultOn == null || !chargeOn.isAfter(defaultOn)) {
				shouldHaveFee.add(i.getId());
			}
		}
		List<LateFee> existing = history.fees(loan.getId());
		Set<Long> haveFee = new HashSet<>();
		for (LateFee fee : existing) {
			haveFee.add(fee.getInstallmentId());
			RepaymentSchedule.Line line = schedule.lineFor(fee.getInstallmentId());
			boolean unpaid = line == null || line.feePaid().signum() == 0;
			if (!shouldHaveFee.contains(fee.getInstallmentId()) && unpaid && !fee.isWaived()) {
				cancel(fee, loan);
			}
		}
		for (Installment i : missed) {
			if (shouldHaveFee.contains(i.getId()) && !haveFee.contains(i.getId())) {
				charge(loan, i);
			}
		}

		if (defaultOn != null && loan.getStatus() != LoanStatus.DEFAULTED) {
			loan.markDefaulted(defaultOn);
		} else if (defaultOn == null && loan.getStatus() == LoanStatus.DEFAULTED && loan.getDefaultedOn() != null
				&& !loan.isAwaitingDecision()) {
			loan.clearDefault(wasRestructured(loan));
		}
	}

	private void charge(Loan loan, Installment installment) {
		LocalDate chargedOn = history.deadline(installment).plusDays(1);
		LateFee fee = fees.save(new LateFee(loan.getId(), installment.getId(), feeFor(installment), chargedOn));
		ledger.post(LedgerEntry.forLateFee(LedgerEntryType.LATE_FEE_CHARGED, fee.getAmount(), loan.getBorrower(),
				chargedOn, loan.getId(), fee.getId(), "Installment " + installment.getNumber() + " missed"));
	}

	private void cancel(LateFee fee, Loan loan) {
		fee.cancel();
		for (LedgerEntry entry : ledgerEntries.findByLateFeeIdAndTypeAndReversesIdIsNull(fee.getId(),
				LedgerEntryType.LATE_FEE_CHARGED)) {
			if (!ledger.isReversed(entry.getId())) {
				ledger.reverse(entry.getId(), "Paid in time after all");
			}
		}
	}

	private boolean wasRestructured(Loan loan) {
		return decisions.findByLoanIdOrderByDecidedOnDescIdDesc(loan.getId()).stream()
				.anyMatch(d -> d.getKind() == GroupDecision.Kind.RESTRUCTURE);
	}
}

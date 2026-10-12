package com.personal.chama.loan;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.personal.chama.common.Money;
import com.personal.chama.config.ChamaProperties;

/**
 * Reads a loan's history and replays it (see {@link RepaymentSchedule}). Also knows the
 * grace period: an installment is MISSED when it isn't fully paid (fee aside) by the end
 * of its due date plus the grace days.
 */
@Component
public class LoanHistory {

	private final InstallmentRepository installments;
	private final LateFeeRepository fees;
	private final ScheduleChangeRepository changes;
	private final RepaymentRepository repayments;
	private final ChamaProperties.Loan settings;
	private final Clock clock;

	public LoanHistory(InstallmentRepository installments, LateFeeRepository fees, ScheduleChangeRepository changes,
			RepaymentRepository repayments, ChamaProperties properties, Clock clock) {
		this.installments = installments;
		this.fees = fees;
		this.changes = changes;
		this.repayments = repayments;
		this.settings = properties.loan();
		this.clock = clock;
	}

	public LocalDate today() {
		return LocalDate.now(clock);
	}

	public RepaymentSchedule schedule(Loan loan) {
		return scheduleAsOf(loan, today());
	}

	public RepaymentSchedule scheduleAsOf(Loan loan, LocalDate asOf) {
		return RepaymentSchedule.replay(installments.findByLoanIdOrderByDueDateAscIdAsc(loan.getId()),
				fees.findByLoanIdAndCancelledFalseOrderByChargedOnAscIdAsc(loan.getId()),
				changes.findByLoanIdOrderByEffectiveDateAscIdAsc(loan.getId()), standingRepayments(loan.getId()), asOf);
	}

	public List<Repayment> standingRepayments(Long loanId) {
		return repayments.findByLoanIdAndVoidedAtIsNullOrderByPaidDateAscIdAsc(loanId);
	}

	public List<LateFee> fees(Long loanId) {
		return fees.findByLoanIdAndCancelledFalseOrderByChargedOnAscIdAsc(loanId);
	}

	public List<ScheduleChange> changes(Long loanId) {
		return changes.findByLoanIdOrderByEffectiveDateAscIdAsc(loanId);
	}

	/** The last day an installment can be paid before it counts as missed. */
	public LocalDate deadline(Installment installment) {
		return installment.getDueDate().plusDays(settings.graceDays());
	}

	public boolean isPastDeadline(Installment installment) {
		return today().isAfter(deadline(installment));
	}

	/** Due date passed but still within the grace days. */
	public boolean isInGrace(Installment installment) {
		return today().isAfter(installment.getDueDate()) && !isPastDeadline(installment);
	}

	/**
	 * Was this installment missed? It was if, by the end of its deadline, its interest and
	 * principal weren't fully paid. A later payment (or recovery) doesn't change that (R3).
	 */
	public boolean isMissed(Loan loan, Installment installment) {
		if (!isPastDeadline(installment)) {
			return false;
		}
		RepaymentSchedule atDeadline = scheduleAsOf(loan, deadline(installment));
		RepaymentSchedule.Line line = atDeadline.lineFor(installment.getId());
		return line != null && line.isActive() && line.scheduledOwed().signum() > 0;
	}

	/** Installments in the current plan that were missed, by due date. */
	public List<Installment> missed(Loan loan, RepaymentSchedule schedule) {
		return schedule.activeLines().stream().map(RepaymentSchedule.Line::installment)
				.filter(i -> isMissed(loan, i)).toList();
	}

	/**
	 * What is due right now: the whole balance once the loan has defaulted (two misses in
	 * a row), otherwise what's owed on installments past their deadline.
	 */
	public BigDecimal dueNow(Loan loan, RepaymentSchedule schedule) {
		if (loan.getStatus() == LoanStatus.DEFAULTED) {
			return schedule.remaining();
		}
		return schedule.activeLines().stream()
				.filter(l -> isPastDeadline(l.installment()))
				.map(RepaymentSchedule.Line::owed)
				.reduce(Money.ZERO, BigDecimal::add);
	}

	/**
	 * Can the recorder recover what's owed from shares now? Once the loan has defaulted,
	 * or an installment was missed. A loan repaid in one payment waits until it is a month
	 * overdue (decision R4).
	 */
	public boolean recoveryAvailable(Loan loan, RepaymentSchedule schedule) {
		if (!loan.isActive() || dueNow(loan, schedule).signum() <= 0) {
			return false;
		}
		if (loan.getStatus() == LoanStatus.DEFAULTED) {
			return true;
		}
		List<RepaymentSchedule.Line> current = schedule.activeLines();
		boolean singlePayment = current.size() == 1;
		return current.stream().anyMatch(l -> !l.isPaid() && isMissed(loan, l.installment())
				&& (!singlePayment || today().isAfter(l.installment().getDueDate().plusMonths(1))));
	}

	/**
	 * The latest date in the loan's history (a repayment or a schedule change). New
	 * payments can't be dated before it, so the replay order never changes.
	 */
	public LocalDate latestEventDate(Loan loan) {
		return Stream.concat(
				standingRepayments(loan.getId()).stream().map(Repayment::getPaidDate),
				changes(loan.getId()).stream().map(ScheduleChange::getEffectiveDate))
				.max(LocalDate::compareTo).orElse(loan.getDisbursedDate());
	}

	public ChamaProperties.Loan settings() {
		return settings;
	}
}

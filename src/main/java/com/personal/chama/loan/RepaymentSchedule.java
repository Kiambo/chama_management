package com.personal.chama.loan;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.personal.chama.common.Money;

/**
 * Where a loan's repayments have gone. Repayments are applied in the order they were
 * paid, each to the oldest unpaid installment first, and within an installment to
 * interest before principal (decision L5; late fees join in Phase 4).
 */
public final class RepaymentSchedule {

	private final List<Line> lines;

	private RepaymentSchedule(List<Line> lines) {
		this.lines = lines;
	}

	public static RepaymentSchedule replay(List<Installment> installments, List<BigDecimal> payments) {
		List<Line> lines = new ArrayList<>();
		for (Installment i : installments) {
			lines.add(new Line(i, Money.ZERO, Money.ZERO));
		}
		RepaymentSchedule schedule = new RepaymentSchedule(lines);
		for (BigDecimal payment : payments) {
			schedule.apply(payment);
		}
		return schedule;
	}

	/** Applies a payment and returns how much of it was interest and how much principal. */
	public Allocation apply(BigDecimal amount) {
		if (amount.compareTo(remaining()) > 0) {
			throw new LoanException("That is more than the whole remaining balance (KES "
					+ Money.format(remaining()) + ").");
		}
		BigDecimal left = amount;
		BigDecimal toInterest = Money.ZERO;
		BigDecimal toPrincipal = Money.ZERO;
		for (int i = 0; i < lines.size() && left.signum() > 0; i++) {
			Line line = lines.get(i);
			BigDecimal interest = left.min(line.interestOwed());
			left = left.subtract(interest);
			BigDecimal principal = left.min(line.principalOwed());
			left = left.subtract(principal);
			lines.set(i, new Line(line.installment(), line.interestPaid().add(interest), line.principalPaid().add(principal)));
			toInterest = toInterest.add(interest);
			toPrincipal = toPrincipal.add(principal);
		}
		return new Allocation(Money.orZero(toInterest), Money.orZero(toPrincipal));
	}

	public BigDecimal remaining() {
		return lines.stream().map(Line::owed).reduce(Money.ZERO, BigDecimal::add);
	}

	public boolean isFullyPaid() {
		return remaining().signum() == 0;
	}

	public List<Line> lines() {
		return List.copyOf(lines);
	}

	/** The first installment not yet fully paid, if any. */
	public Line nextDue() {
		return lines.stream().filter(l -> !l.isPaid()).findFirst().orElse(null);
	}

	public record Allocation(BigDecimal interest, BigDecimal principal) {
	}

	public record Line(Installment installment, BigDecimal interestPaid, BigDecimal principalPaid) {

		public BigDecimal interestOwed() {
			return installment.getInterestPart().subtract(interestPaid);
		}

		public BigDecimal principalOwed() {
			return installment.getPrincipalPart().subtract(principalPaid);
		}

		public BigDecimal owed() {
			return interestOwed().add(principalOwed());
		}

		public BigDecimal paid() {
			return interestPaid.add(principalPaid);
		}

		public boolean isPaid() {
			return owed().signum() == 0;
		}
	}
}

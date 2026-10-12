package com.personal.chama.loan;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.personal.chama.common.Money;

/**
 * Where a loan's payments have gone, worked out by replaying its history in order:
 * repayments and schedule changes by date (then by when they were recorded). Each payment
 * goes to the oldest unpaid installment first and, within it, to the late fee, then
 * interest, then principal (decision L5). A late fee only counts from the day it was
 * charged, so a payment made before then never goes to it.
 *
 * Nothing here is stored: it is recomputed from the installments, fees, changes and
 * repayments, which is why the same split always comes out.
 */
public final class RepaymentSchedule {

	private final Map<Long, Line> linesByInstallment = new LinkedHashMap<>();
	private final List<Line> lines = new ArrayList<>();
	private final List<LateFee> fees;
	private final Set<Long> appliedFees = new HashSet<>();
	/** The date of the payment being applied, so each installment knows when it was paid off. */
	private LocalDate currentDate;

	private RepaymentSchedule(List<Installment> installments, List<LateFee> fees) {
		this.fees = fees;
		for (Installment i : installments) {
			Line line = new Line(i, i.getScheduleChangeId() == null);
			lines.add(line);
			if (i.getId() != null) {
				linesByInstallment.put(i.getId(), line);
			}
		}
	}

	/** Replays everything up to and including {@code asOf}. */
	public static RepaymentSchedule replay(List<Installment> installments, List<LateFee> fees,
			List<ScheduleChange> changes, List<Repayment> repayments, LocalDate asOf) {
		RepaymentSchedule schedule = new RepaymentSchedule(installments, fees);
		List<Event> events = new ArrayList<>();
		for (Repayment r : repayments) {
			events.add(new Event(r.getPaidDate(), r.getRecordedAt(), r, null));
		}
		for (ScheduleChange c : changes) {
			events.add(new Event(c.getEffectiveDate(), c.getRecordedAt(), null, c));
		}
		events.sort(Comparator.comparing(Event::date)
				.thenComparing(Event::recordedAt, Comparator.nullsLast(Comparator.naturalOrder())));
		for (Event e : events) {
			if (e.date().isAfter(asOf)) {
				break;
			}
			schedule.chargeFeesUpTo(e.date());
			schedule.currentDate = e.date();
			if (e.repayment() != null) {
				schedule.allocate(e.repayment().getAmount());
			} else {
				schedule.switchTo(e.change().getId());
			}
		}
		schedule.chargeFeesUpTo(asOf);
		return schedule;
	}

	/** The original schedule with plain payments, for tests and previews. */
	public static RepaymentSchedule replay(List<Installment> installments, List<BigDecimal> payments) {
		RepaymentSchedule schedule = new RepaymentSchedule(installments, List.of());
		payments.forEach(schedule::allocate);
		return schedule;
	}

	/** Applies a new payment made on {@code date} and says how it splits. */
	public Allocation apply(BigDecimal amount, LocalDate date) {
		chargeFeesUpTo(date);
		currentDate = date;
		return allocate(amount);
	}

	/** Applies a new payment, with fees already charged. */
	public Allocation apply(BigDecimal amount) {
		return allocate(amount);
	}

	private Allocation allocate(BigDecimal amount) {
		if (amount.compareTo(remaining()) > 0) {
			throw new LoanException("That is more than the whole remaining balance (KES "
					+ Money.format(remaining()) + ").");
		}
		BigDecimal left = amount;
		BigDecimal toFee = Money.ZERO;
		BigDecimal toInterest = Money.ZERO;
		BigDecimal toPrincipal = Money.ZERO;
		for (Line line : activeLines()) {
			if (left.signum() == 0) {
				break;
			}
			BigDecimal fee = left.min(line.feeOwed());
			line.feePaid = line.feePaid.add(fee);
			left = left.subtract(fee);
			BigDecimal interest = left.min(line.interestOwed());
			line.interestPaid = line.interestPaid.add(interest);
			left = left.subtract(interest);
			BigDecimal principal = left.min(line.principalOwed());
			line.principalPaid = line.principalPaid.add(principal);
			left = left.subtract(principal);
			if (line.isPaid() && line.paidOn == null) {
				line.paidOn = currentDate;
			}
			toFee = toFee.add(fee);
			toInterest = toInterest.add(interest);
			toPrincipal = toPrincipal.add(principal);
		}
		return new Allocation(Money.orZero(toFee), Money.orZero(toInterest), Money.orZero(toPrincipal));
	}

	private void chargeFeesUpTo(LocalDate date) {
		for (LateFee fee : fees) {
			if (!fee.isCancelled() && !fee.getChargedOn().isAfter(date) && appliedFees.add(fee.getId())) {
				Line line = linesByInstallment.get(fee.getInstallmentId());
				if (line != null) {
					line.feeDue = line.feeDue.add(fee.payable());
				}
			}
		}
	}

	/** From a schedule change on: the installments it replaces stop, the new ones start. */
	private void switchTo(Long changeId) {
		for (Line line : lines) {
			if (changeId.equals(line.installment.getSupersededByChangeId())) {
				line.active = false;
			}
			if (changeId.equals(line.installment.getScheduleChangeId())) {
				line.active = true;
			}
		}
	}

	/** Installments in the current plan, by due date. */
	public List<Line> activeLines() {
		return lines.stream().filter(l -> l.active).sorted(Comparator.comparing(l -> l.installment.getDueDate())).toList();
	}

	/** Every installment the loan has had, superseded ones included, by due date. */
	public List<Line> lines() {
		return lines.stream().sorted(Comparator.comparing((Line l) -> l.installment.getDueDate())
				.thenComparing(l -> l.active)).toList();
	}

	public Line lineFor(Long installmentId) {
		return linesByInstallment.get(installmentId);
	}

	public BigDecimal remaining() {
		return activeLines().stream().map(Line::owed).reduce(Money.ZERO, BigDecimal::add);
	}

	public BigDecimal interestOwed() {
		return activeLines().stream().map(Line::interestOwed).reduce(Money.ZERO, BigDecimal::add);
	}

	public BigDecimal principalOwed() {
		return activeLines().stream().map(Line::principalOwed).reduce(Money.ZERO, BigDecimal::add);
	}

	public BigDecimal feesOwed() {
		return activeLines().stream().map(Line::feeOwed).reduce(Money.ZERO, BigDecimal::add);
	}

	public boolean isFullyPaid() {
		return remaining().signum() == 0;
	}

	/** The first installment in the current plan not yet fully paid, if any. */
	public Line nextDue() {
		return activeLines().stream().filter(l -> !l.isPaid()).findFirst().orElse(null);
	}

	public record Allocation(BigDecimal fee, BigDecimal interest, BigDecimal principal) {

		public BigDecimal total() {
			return fee.add(interest).add(principal);
		}
	}

	private record Event(LocalDate date, Instant recordedAt, Repayment repayment, ScheduleChange change) {
	}

	/** One installment and what has been paid towards it. */
	public static final class Line {

		private final Installment installment;
		private boolean active;
		private BigDecimal feeDue = Money.ZERO;
		private BigDecimal feePaid = Money.ZERO;
		private BigDecimal interestPaid = Money.ZERO;
		private BigDecimal principalPaid = Money.ZERO;
		private LocalDate paidOn;

		private Line(Installment installment, boolean active) {
			this.installment = installment;
			this.active = active;
		}

		public Installment installment() {
			return installment;
		}

		/** When the installment was paid off: the date of the payment that cleared it, if known. */
		public LocalDate paidOn() {
			return isPaid() ? paidOn : null;
		}

		public boolean isActive() {
			return active;
		}

		public BigDecimal feeDue() {
			return feeDue;
		}

		public BigDecimal feePaid() {
			return feePaid;
		}

		public BigDecimal feeOwed() {
			return feeDue.subtract(feePaid).max(Money.ZERO);
		}

		public BigDecimal interestPaid() {
			return interestPaid;
		}

		public BigDecimal principalPaid() {
			return principalPaid;
		}

		public BigDecimal interestOwed() {
			return installment.getInterestPart().subtract(interestPaid);
		}

		public BigDecimal principalOwed() {
			return installment.getPrincipalPart().subtract(principalPaid);
		}

		/** Interest and principal still owed, without the late fee. */
		public BigDecimal scheduledOwed() {
			return interestOwed().add(principalOwed());
		}

		public BigDecimal owed() {
			return scheduledOwed().add(feeOwed());
		}

		public BigDecimal paid() {
			return feePaid.add(interestPaid).add(principalPaid);
		}

		public boolean isPaid() {
			return owed().signum() == 0;
		}
	}
}

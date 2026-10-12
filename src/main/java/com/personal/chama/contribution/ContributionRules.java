package com.personal.chama.contribution;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Component;

import com.personal.chama.common.Money;
import com.personal.chama.config.ChamaProperties;
import com.personal.chama.member.Member;

/**
 * The contribution rules (decisions C1-C5): what is due, how a payment is split, and
 * the status of a month. Pure logic - no database - so every rule is unit-tested.
 */
@Component
public class ContributionRules {

	private final ChamaProperties.Contribution settings;
	private final YearMonth cutover;

	public ContributionRules(ChamaProperties properties) {
		this.settings = properties.contribution();
		this.cutover = properties.cutoverMonth();
	}

	public LocalDate dueDate(YearMonth month) {
		return month.atDay(Math.min(settings.dueDay(), month.lengthOfMonth()));
	}

	public boolean isMemberIn(Member member, YearMonth month) {
		return !month.isBefore(YearMonth.from(member.getJoinDate()));
	}

	public boolean isInMgr(Member member, YearMonth month) {
		return member.getMgrStartDate() != null && !month.isBefore(YearMonth.from(member.getMgrStartDate()));
	}

	/** Savings are due unless opted out; MGR is due once in the rotation, unless opted out. */
	public Portions due(Member member, YearMonth month, ContributionOptOut optOut) {
		if (!isMemberIn(member, month)) {
			return Portions.NONE;
		}
		boolean savingsOut = optOut != null && optOut.isOptOutSavings();
		boolean mgrOut = optOut != null && optOut.isOptOutMgr();
		BigDecimal mmf = savingsOut ? Money.ZERO : Money.of(settings.mmfAmount());
		BigDecimal mgr = isInMgr(member, month) && !mgrOut ? Money.of(settings.mgrAmount()) : Money.ZERO;
		return new Portions(mmf, mgr);
	}

	/**
	 * Savings first, then MGR (decision C2). Paying more than is still owed for the
	 * month is refused: each month is recorded separately (Phase 2 plan, Q2).
	 */
	public Portions split(BigDecimal amount, Portions due, Portions alreadyPaid) {
		if (amount.signum() <= 0) {
			throw new ContributionException("The amount must be more than zero.");
		}
		Portions owed = due.remainingAfter(alreadyPaid);
		if (amount.compareTo(owed.total()) > 0) {
			throw new ContributionException(owed.total().signum() == 0
					? "Nothing more is owed for this month."
					: "That is more than is owed for this month (KES " + Money.format(owed.total())
							+ "). Record other months separately.");
		}
		BigDecimal toMmf = amount.min(owed.mmf());
		BigDecimal toMgr = amount.subtract(toMmf);
		return new Portions(Money.orZero(toMmf), Money.orZero(toMgr));
	}

	public MonthStatus status(YearMonth month, Portions due, List<Contribution> payments, LocalDate today) {
		Portions paid = paidBy(payments);
		if (month.isBefore(cutover) && payments.isEmpty()) {
			return MonthStatus.NOT_TRACKED;
		}
		if (due.isZero()) {
			return MonthStatus.OPTED_OUT;
		}
		if (paid.total().signum() == 0) {
			return today.isAfter(dueDate(month)) ? MonthStatus.MISSED : MonthStatus.DUE;
		}
		if (paid.total().compareTo(due.total()) < 0) {
			return MonthStatus.PARTLY_PAID;
		}
		LocalDate dueDate = dueDate(month);
		boolean late = payments.stream().anyMatch(c -> !c.isAgreedBackpay() && c.getPaidDate().isAfter(dueDate));
		return late ? MonthStatus.PAID_LATE : MonthStatus.PAID;
	}

	public static Portions paidBy(List<Contribution> payments) {
		return payments.stream()
				.map(c -> new Portions(c.getMmfPortion(), c.getMgrPortion()))
				.reduce(Portions.NONE, Portions::plus);
	}
}

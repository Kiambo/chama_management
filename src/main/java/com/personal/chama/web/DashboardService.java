package com.personal.chama.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.chama.common.Money;
import com.personal.chama.config.ChamaProperties;
import com.personal.chama.contribution.ContributionService;
import com.personal.chama.contribution.MemberMonth;
import com.personal.chama.contribution.MonthStatus;
import com.personal.chama.ledger.LedgerService;
import com.personal.chama.loan.LoanFlag;
import com.personal.chama.loan.LoanService;
import com.personal.chama.mgr.MgrService;
import com.personal.chama.pool.OpeningBalanceService;

@Service
public class DashboardService {

	private final ContributionService contributions;
	private final LedgerService ledger;
	private final OpeningBalanceService openingBalances;
	private final LoanService loans;
	private final MgrService mgr;
	private final ChamaProperties properties;
	private final Clock clock;

	public DashboardService(ContributionService contributions, LedgerService ledger,
			OpeningBalanceService openingBalances, LoanService loans, MgrService mgr, ChamaProperties properties,
			Clock clock) {
		this.contributions = contributions;
		this.ledger = ledger;
		this.openingBalances = openingBalances;
		this.loans = loans;
		this.mgr = mgr;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Dashboard build() {
		YearMonth month = contributions.currentMonth();
		List<MemberMonth> rows = contributions.monthOverview(month);
		List<MemberMonth> expected = rows.stream().filter(r -> r.status() != MonthStatus.OPTED_OUT).toList();
		long paidIn = expected.stream().filter(r -> r.status().isComplete()).count();

		BigDecimal mgrExpected = rows.stream().map(r -> r.due().mgr()).reduce(Money.ZERO, BigDecimal::add);
		BigDecimal mgrCollected = ledger.mgrCollectedFor(month);

		// The "total chama fund": cash plus principal out on loan (decision P1).
		BigDecimal poolTotal = ledger.poolTotal();
		BigDecimal goal = Money.of(properties.goalAmount());
		int goalPercent = goal.signum() == 0 ? 0
				: poolTotal.multiply(BigDecimal.valueOf(100)).divide(goal, 0, RoundingMode.DOWN).max(BigDecimal.ZERO)
						.min(BigDecimal.valueOf(100)).intValue();

		List<LoanService.LoanView> activeLoans = loans.active();
		return new Dashboard(month, rows, paidIn, expected.size(), ledger.mmfBalance(), ledger.mmfChangeIn(month),
				mgrCollected, mgrExpected, mgrExpected.subtract(mgrCollected).max(Money.ZERO), goal, goalPercent,
				openingBalances.isRecorded(), activeLoans, ledger.principalOutstanding(), loanNote(activeLoans),
				rotation(month, mgrExpected));
	}

	/** Who receives this month and who is next, from the current round. */
	private Rotation rotation(YearMonth month, BigDecimal pool) {
		return mgr.currentRound().map(round -> {
			MgrService.SlotView now = round.forMonth(month).orElse(null);
			MgrService.SlotView next = round.nextAfter(month).orElse(null);
			BigDecimal amount = now != null && !now.payouts().isEmpty() ? now.gross() : pool;
			return new Rotation(round.round().getNumber(), now == null ? 0 : round.indexOf(now),
					round.activeSlots().size(), now, next, amount, payoutStatus(now, month));
		}).orElse(null);
	}

	private String payoutStatus(MgrService.SlotView slot, YearMonth month) {
		return slot == null ? null
				: payoutStatus(slot.status(), month, LocalDate.now(clock), properties.contribution().dueDay());
	}

	/** Paid; Pending up to the due day (the 10th, the latest a payout should happen); Late after it (M1). */
	static String payoutStatus(MgrService.SlotStatus status, YearMonth month, LocalDate today, int dueDay) {
		if (status == MgrService.SlotStatus.PAID) {
			return "Paid";
		}
		LocalDate latest = month.atDay(Math.min(dueDay, month.lengthOfMonth()));
		return today.isAfter(latest) ? "Late" : "Pending";
	}

	public record Rotation(int round, int position, int length, MgrService.SlotView receiving,
			MgrService.SlotView next, BigDecimal amount, String payoutStatus) {

		public String payoutTone() {
			return switch (payoutStatus == null ? "" : payoutStatus) {
				case "Paid" -> "green";
				case "Late" -> "red";
				default -> "amber";
			};
		}
	}

	/** "One payment due Fri 16 Oct", "2 payments overdue", "All on track" - the most urgent first. */
	static String loanNote(List<LoanService.LoanView> active) {
		if (active.isEmpty()) {
			return "None out";
		}
		long overdue = active.stream().filter(v -> v.flag() == LoanFlag.OVERDUE).count();
		if (overdue > 0) {
			return overdue == 1 ? "One payment overdue" : overdue + " payments overdue";
		}
		List<LoanService.LoanView> dueSoon = active.stream().filter(v -> v.flag() == LoanFlag.DUE_SOON).toList();
		if (dueSoon.size() == 1) {
			return "One payment due " + dueSoon.get(0).nextDue().installment().getDueDate()
					.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.ENGLISH));
		}
		if (dueSoon.size() > 1) {
			return dueSoon.size() + " payments due soon";
		}
		return "All on track";
	}

	public record Dashboard(YearMonth month, List<MemberMonth> rows, long paidIn, int expectedCount,
			BigDecimal mmfBalance, BigDecimal mmfChangeThisMonth, BigDecimal mgrCollected, BigDecimal mgrExpected,
			BigDecimal mgrOutstanding, BigDecimal goal, int goalPercent, boolean openingRecorded,
			List<LoanService.LoanView> activeLoans, BigDecimal principalOutstanding, String loanNote, Rotation rotation) {

		public boolean loanAttention() {
			return activeLoans.stream().anyMatch(v -> v.flag() == LoanFlag.OVERDUE || v.flag() == LoanFlag.DUE_SOON);
		}

		public boolean loanOverdue() {
			return activeLoans.stream().anyMatch(v -> v.flag() == LoanFlag.OVERDUE);
		}
	}
}

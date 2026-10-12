package com.personal.chama.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.chama.common.Money;
import com.personal.chama.config.ChamaProperties;
import com.personal.chama.contribution.ContributionService;
import com.personal.chama.contribution.MemberMonth;
import com.personal.chama.contribution.MonthStatus;
import com.personal.chama.ledger.LedgerService;
import com.personal.chama.pool.OpeningBalanceService;

@Service
public class DashboardService {

	private final ContributionService contributions;
	private final LedgerService ledger;
	private final OpeningBalanceService openingBalances;
	private final ChamaProperties properties;

	public DashboardService(ContributionService contributions, LedgerService ledger,
			OpeningBalanceService openingBalances, ChamaProperties properties) {
		this.contributions = contributions;
		this.ledger = ledger;
		this.openingBalances = openingBalances;
		this.properties = properties;
	}

	@Transactional(readOnly = true)
	public Dashboard build() {
		YearMonth month = contributions.currentMonth();
		List<MemberMonth> rows = contributions.monthOverview(month);
		List<MemberMonth> expected = rows.stream().filter(r -> r.status() != MonthStatus.OPTED_OUT).toList();
		long paidIn = expected.stream().filter(r -> r.status().isComplete()).count();

		BigDecimal mgrExpected = rows.stream().map(r -> r.due().mgr()).reduce(Money.ZERO, BigDecimal::add);
		BigDecimal mgrCollected = ledger.mgrCollectedFor(month);

		// Until loans exist (Phase 3) the total fund is the MMF cash balance.
		BigDecimal poolTotal = ledger.mmfBalance();
		BigDecimal goal = Money.of(properties.goalAmount());
		int goalPercent = goal.signum() == 0 ? 0
				: poolTotal.multiply(BigDecimal.valueOf(100)).divide(goal, 0, RoundingMode.DOWN).max(BigDecimal.ZERO)
						.min(BigDecimal.valueOf(100)).intValue();

		return new Dashboard(month, rows, paidIn, expected.size(), poolTotal, ledger.mmfChangeIn(month),
				mgrCollected, mgrExpected, mgrExpected.subtract(mgrCollected).max(Money.ZERO), goal, goalPercent,
				openingBalances.isRecorded());
	}

	public record Dashboard(YearMonth month, List<MemberMonth> rows, long paidIn, int expectedCount,
			BigDecimal mmfBalance, BigDecimal mmfChangeThisMonth, BigDecimal mgrCollected, BigDecimal mgrExpected,
			BigDecimal mgrOutstanding, BigDecimal goal, int goalPercent, boolean openingRecorded) {
	}
}

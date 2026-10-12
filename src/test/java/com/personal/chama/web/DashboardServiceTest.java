package com.personal.chama.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

import com.personal.chama.mgr.MgrService.SlotStatus;

class DashboardServiceTest {

	private static final YearMonth OCT = YearMonth.of(2026, 10);

	@Test
	void payoutStatusOnTheDashboard() {
		assertThat(DashboardService.payoutStatus(SlotStatus.PAID, OCT, LocalDate.of(2026, 10, 20), 10)).isEqualTo("Paid");
		assertThat(DashboardService.payoutStatus(SlotStatus.DUE, OCT, LocalDate.of(2026, 10, 3), 10)).isEqualTo("Pending");
		assertThat(DashboardService.payoutStatus(SlotStatus.DUE, OCT, LocalDate.of(2026, 10, 10), 10)).isEqualTo("Pending");
		assertThat(DashboardService.payoutStatus(SlotStatus.DUE, OCT, LocalDate.of(2026, 10, 11), 10)).isEqualTo("Late");
	}
}

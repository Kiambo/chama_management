package com.personal.chama.contribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.personal.chama.common.Money;
import com.personal.chama.config.ChamaProperties;
import com.personal.chama.member.Member;

class ContributionRulesTest {

	private static final YearMonth OCT = YearMonth.of(2026, 10);
	private static final YearMonth CUTOVER = OCT;

	private final ContributionRules rules = new ContributionRules(new ChamaProperties(
			new ChamaProperties.Contribution(new BigDecimal("2000"), new BigDecimal("1500"), 10),
			CUTOVER, new BigDecimal("250000")));

	private static Member member(String joined, String mgrStart) {
		return new Member("Test Member", LocalDate.parse(joined), mgrStart == null ? null : LocalDate.parse(mgrStart));
	}

	private static Portions portions(long mmf, long mgr) {
		return new Portions(Money.of(mmf), Money.of(mgr));
	}

	private static ContributionOptOut optOut(boolean savings, boolean mgr) {
		ContributionOptOut optOut = new ContributionOptOut(null, OCT);
		optOut.set(savings, mgr, null);
		return optOut;
	}

	private static Contribution paid(long mmf, long mgr, String date, boolean agreedBackpay) {
		return new Contribution(null, OCT, portions(mmf, mgr), LocalDate.parse(date), "M-Pesa", null, agreedBackpay, null);
	}

	@Nested
	class WhatIsDue {

		@Test
		void fullMemberOwesBothParts() {
			assertThat(rules.due(member("2025-03-01", "2025-03-01"), OCT, null)).isEqualTo(portions(2000, 1500));
		}

		@Test
		void memberNotYetInMgrOwesSavingsOnly() {
			// The new member: joined Sep 2026, MGR from Jan 2027.
			Member newMember = member("2026-09-01", "2027-01-01");
			assertThat(rules.due(newMember, YearMonth.of(2026, 12), null)).isEqualTo(portions(2000, 0));
			assertThat(rules.due(newMember, YearMonth.of(2027, 1), null)).isEqualTo(portions(2000, 1500));
		}

		@Test
		void memberWithNoMgrStartOwesSavingsOnly() {
			assertThat(rules.due(member("2025-03-01", null), OCT, null)).isEqualTo(portions(2000, 0));
		}

		@Test
		void optOutsRemoveTheirPart() {
			Member m = member("2025-03-01", "2025-03-01");
			assertThat(rules.due(m, OCT, optOut(true, false))).isEqualTo(portions(0, 1500));
			assertThat(rules.due(m, OCT, optOut(false, true))).isEqualTo(portions(2000, 0));
			assertThat(rules.due(m, OCT, optOut(true, true))).isEqualTo(Portions.NONE);
		}

		@Test
		void nothingIsDueBeforeJoining() {
			assertThat(rules.due(member("2026-11-15", "2026-11-15"), OCT, null)).isEqualTo(Portions.NONE);
		}
	}

	@Nested
	class SplittingAPayment {

		/** The table in the Phase 2 plan, section 3. */
		@ParameterizedTest(name = "pay {0} with {1}+{2} due -> {3} savings, {4} MGR")
		@CsvSource({
				"3500, 2000, 1500, 2000, 1500",
				"2000, 2000, 1500, 2000,    0",
				"1500, 2000, 1500, 1500,    0",
				"2000, 2000,    0, 2000,    0",
				"1500,    0, 1500,    0, 1500" })
		void savingsFirstThenMgr(long amount, long dueMmf, long dueMgr, long toMmf, long toMgr) {
			Portions split = rules.split(Money.of(amount), portions(dueMmf, dueMgr), Portions.NONE);
			assertThat(split).isEqualTo(portions(toMmf, toMgr));
		}

		@Test
		void aSecondPaymentTopsUpWhatIsStillOwed() {
			Portions split = rules.split(Money.of(1500), portions(2000, 1500), portions(2000, 0));
			assertThat(split).isEqualTo(portions(0, 1500));
		}

		@Test
		void aPartPaymentAcrossBothParts() {
			Portions split = rules.split(Money.of(1000), portions(2000, 1500), portions(1500, 0));
			assertThat(split).isEqualTo(portions(500, 500));
		}

		@Test
		void moreThanIsOwedIsRefused() {
			assertThatThrownBy(() -> rules.split(Money.of(7000), portions(2000, 1500), Portions.NONE))
					.isInstanceOf(ContributionException.class)
					.hasMessageContaining("more than is owed");
		}

		@Test
		void payingWhenNothingIsOwedIsRefused() {
			assertThatThrownBy(() -> rules.split(Money.of(100), portions(2000, 1500), portions(2000, 1500)))
					.isInstanceOf(ContributionException.class)
					.hasMessageContaining("Nothing more is owed");
		}

		@Test
		void zeroIsRefused() {
			assertThatThrownBy(() -> rules.split(Money.ZERO, portions(2000, 1500), Portions.NONE))
					.isInstanceOf(ContributionException.class);
		}

		@Test
		void centsAreKept() {
			Portions split = rules.split(new BigDecimal("2000.50"), portions(2000, 1500), Portions.NONE);
			assertThat(split).isEqualTo(new Portions(Money.of(2000), new BigDecimal("0.50")));
		}
	}

	@Nested
	class MonthStatusRules {

		private final Portions full = portions(2000, 1500);
		private final LocalDate afterDueDay = LocalDate.of(2026, 10, 20);

		@Test
		void paidOnTheDueDayIsOnTime() {
			assertThat(rules.status(OCT, full, List.of(paid(2000, 1500, "2026-10-10", false)), afterDueDay))
					.isEqualTo(MonthStatus.PAID);
		}

		@Test
		void paidTheDayAfterIsLate() {
			assertThat(rules.status(OCT, full, List.of(paid(2000, 1500, "2026-10-11", false)), afterDueDay))
					.isEqualTo(MonthStatus.PAID_LATE);
		}

		@Test
		void lastPartPaidLateMakesTheMonthLate() {
			List<Contribution> payments = List.of(paid(2000, 0, "2026-10-05", false), paid(0, 1500, "2026-10-15", false));
			assertThat(rules.status(OCT, full, payments, afterDueDay)).isEqualTo(MonthStatus.PAID_LATE);
		}

		@Test
		void agreedBackPaymentCountsAsOnTime() {
			assertThat(rules.status(OCT, full, List.of(paid(2000, 1500, "2027-01-15", true)), afterDueDay))
					.isEqualTo(MonthStatus.PAID);
		}

		@Test
		void somethingPaidSomethingOwedIsPartlyPaid() {
			assertThat(rules.status(OCT, full, List.of(paid(2000, 0, "2026-10-05", false)), afterDueDay))
					.isEqualTo(MonthStatus.PARTLY_PAID);
		}

		@Test
		void nothingPaidBeforeTheDueDayIsDue() {
			assertThat(rules.status(OCT, full, List.of(), LocalDate.of(2026, 10, 10))).isEqualTo(MonthStatus.DUE);
		}

		@Test
		void nothingPaidAfterTheDueDayIsMissed() {
			assertThat(rules.status(OCT, full, List.of(), LocalDate.of(2026, 10, 11))).isEqualTo(MonthStatus.MISSED);
		}

		@Test
		void optedOutOfEverythingIsOptedOut() {
			assertThat(rules.status(OCT, Portions.NONE, List.of(), afterDueDay)).isEqualTo(MonthStatus.OPTED_OUT);
		}

		@Test
		void monthsBeforeTheCutoverAreNotTrackedUnlessSomethingWasPaid() {
			YearMonth sep = YearMonth.of(2026, 9);
			assertThat(rules.status(sep, full, List.of(), afterDueDay)).isEqualTo(MonthStatus.NOT_TRACKED);
			Contribution backPay = new Contribution(null, sep, portions(2000, 0), LocalDate.of(2026, 10, 5),
					"M-Pesa", null, true, null);
			assertThat(rules.status(sep, portions(2000, 0), List.of(backPay), afterDueDay)).isEqualTo(MonthStatus.PAID);
		}

		@Test
		void dueDayIsCappedAtTheEndOfShortMonths() {
			ContributionRules day31 = new ContributionRules(new ChamaProperties(
					new ChamaProperties.Contribution(new BigDecimal("2000"), new BigDecimal("1500"), 31),
					CUTOVER, new BigDecimal("250000")));
			assertThat(day31.dueDate(YearMonth.of(2027, 2))).isEqualTo(LocalDate.of(2027, 2, 28));
		}
	}
}

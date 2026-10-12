package com.personal.chama.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.personal.chama.common.Money;
import com.personal.chama.config.ChamaProperties;

class LoanCalculatorTest {

	static final ChamaProperties PROPERTIES = new ChamaProperties(null, null, null, new ChamaProperties.Loan(
			new BigDecimal("3"), new BigDecimal("50"), new BigDecimal("60"), new BigDecimal("20000"),
			new BigDecimal("20000"), new BigDecimal("35000"), 7,
			new ChamaProperties.Rates(new BigDecimal("1.5"), new BigDecimal("3"), new BigDecimal("5"), new BigDecimal("8")),
			5, new BigDecimal("500"), new BigDecimal("5")));

	private final LoanCalculator calculator = new LoanCalculator(PROPERTIES);
	private static final LocalDate DISBURSED = LocalDate.of(2026, 10, 15);

	private List<LoanCalculator.PlannedInstallment> plan(long principal, Tenure tenure) {
		return LoanCalculator.schedule(Money.of(principal), calculator.ratePercent(tenure), tenure, DISBURSED);
	}

	@Nested
	class BriefTestCases {

		/** Brief 4.4, "Test cases (must pass)". Installment 0 means a single payment of the total. */
		@ParameterizedTest(name = "{0} over {1}: interest {2}, total {3}, installment {4}")
		@CsvSource({
				"15000, TWO_WEEKS,     225, 15225,    0",
				"15000, ONE_MONTH,     450, 15450,    0",
				"15000, TWO_MONTHS,    750, 15750, 7875",
				"15000, THREE_MONTHS,  750, 15750, 5250",
				"15000, FOUR_MONTHS,  1200, 16200, 4050",
				"15000, SIX_MONTHS,   1200, 16200, 2700",
				"50000, SIX_MONTHS,   4000, 54000, 9000" })
		void briefTestCase(long principal, Tenure tenure, long interest, long total, long installment) {
			BigDecimal rate = calculator.ratePercent(tenure);
			assertThat(LoanCalculator.interest(Money.of(principal), rate)).isEqualByComparingTo(Money.of(interest));

			List<LoanCalculator.PlannedInstallment> plan = plan(principal, tenure);
			BigDecimal sum = plan.stream().map(LoanCalculator.PlannedInstallment::amount).reduce(Money.ZERO, BigDecimal::add);
			assertThat(sum).isEqualByComparingTo(Money.of(total));
			if (installment == 0) {
				assertThat(plan).singleElement().satisfies(i -> assertThat(i.amount()).isEqualByComparingTo(Money.of(total)));
			} else {
				assertThat(plan).hasSize(tenure.getMonths())
						.allSatisfy(i -> assertThat(i.amount()).isEqualByComparingTo(Money.of(installment)));
			}
		}
	}

	@Nested
	class Rounding {

		@Test
		void interestRoundsHalfUpToWholeShillings() {
			// 10,010 x 1.5% = 150.15 -> 150; 10,030 x 1.5% = 150.45 -> 150; 10,034 x 1.5% = 150.51 -> 151
			assertThat(LoanCalculator.interest(Money.of(10_010), new BigDecimal("1.5"))).isEqualByComparingTo("150");
			assertThat(LoanCalculator.interest(Money.of(10_034), new BigDecimal("1.5"))).isEqualByComparingTo("151");
			// 10,100 x 5% = 505 exactly; 10,010 x 5% = 500.50 -> 501 (half up)
			assertThat(LoanCalculator.interest(Money.of(10_010), new BigDecimal("5"))).isEqualByComparingTo("501");
		}

		@Test
		void remainderGoesOnTheLastInstallment() {
			// 10,001 at 5% = 500.05 -> 500; total 10,501 over 3: 3,500 + 3,500 + 3,501
			List<LoanCalculator.PlannedInstallment> plan = plan(10_001, Tenure.THREE_MONTHS);
			assertThat(plan).extracting(LoanCalculator.PlannedInstallment::amount)
					.containsExactly(Money.of(3500), Money.of(3500), Money.of(3501));
		}

		@Test
		void interestAndPrincipalPartsAlwaysAddUpExactly() {
			for (Tenure tenure : Tenure.values()) {
				for (long principal : new long[] { 999, 10_001, 15_000, 33_333, 50_000 }) {
					List<LoanCalculator.PlannedInstallment> plan = plan(principal, tenure);
					BigDecimal interest = LoanCalculator.interest(Money.of(principal), calculator.ratePercent(tenure));
					assertThat(plan.stream().map(LoanCalculator.PlannedInstallment::interestPart).reduce(Money.ZERO, BigDecimal::add))
							.as("interest for %s over %s", principal, tenure).isEqualByComparingTo(interest);
					assertThat(plan.stream().map(LoanCalculator.PlannedInstallment::principalPart).reduce(Money.ZERO, BigDecimal::add))
							.as("principal for %s over %s", principal, tenure).isEqualByComparingTo(Money.of(principal));
					assertThat(plan).allSatisfy(i -> assertThat(i.principalPart().signum()).isGreaterThanOrEqualTo(0));
				}
			}
		}
	}

	@Nested
	class TiersAndDates {

		@Test
		void eachTenureHasItsTier() {
			assertThat(calculator.ratePercent(Tenure.TWO_WEEKS)).isEqualByComparingTo("1.5");
			assertThat(calculator.ratePercent(Tenure.ONE_MONTH)).isEqualByComparingTo("3");
			assertThat(calculator.ratePercent(Tenure.TWO_MONTHS)).isEqualByComparingTo("5");
			assertThat(calculator.ratePercent(Tenure.THREE_MONTHS)).isEqualByComparingTo("5");
			assertThat(calculator.ratePercent(Tenure.FOUR_MONTHS)).isEqualByComparingTo("8");
			assertThat(calculator.ratePercent(Tenure.FIVE_MONTHS)).isEqualByComparingTo("8");
			assertThat(calculator.ratePercent(Tenure.SIX_MONTHS)).isEqualByComparingTo("8");
		}

		@Test
		void twoWeekLoanIsDueOnDay14() {
			assertThat(plan(15_000, Tenure.TWO_WEEKS)).singleElement()
					.satisfies(i -> assertThat(i.dueDate()).isEqualTo(DISBURSED.plusDays(14)));
		}

		@Test
		void installmentsAreMonthlyFromOneMonthAfterDisbursement() {
			assertThat(plan(15_000, Tenure.THREE_MONTHS)).extracting(LoanCalculator.PlannedInstallment::dueDate)
					.containsExactly(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 12, 15), LocalDate.of(2027, 1, 15));
		}

		@Test
		void onePaymentAtTheEndForALoanRecordedAsAgreed() {
			// An existing 50,000 loan over 4 months, repaid in one go at the end.
			List<LoanCalculator.PlannedInstallment> plan = LoanCalculator.singlePaymentAtEnd(Money.of(50_000),
					new BigDecimal("8"), Tenure.FOUR_MONTHS, LocalDate.of(2026, 8, 28));
			assertThat(plan).singleElement().satisfies(i -> {
				assertThat(i.dueDate()).isEqualTo(LocalDate.of(2026, 12, 28));
				assertThat(i.amount()).isEqualByComparingTo("54000");
				assertThat(i.interestPart()).isEqualByComparingTo("4000");
			});
		}
	}

	@Nested
	class Limits {

		@Test
		void exampleFromThePlan() {
			// Shares 34,000; cash 140,000; 50,000 out on loan -> pool total 190,000.
			BorrowingLimit limit = calculator.limit(Money.of(34_000), Money.of(190_000), Money.of(50_000));
			assertThat(limit.byShares()).isEqualByComparingTo("102000");
			assertThat(limit.perLoanCap()).isEqualByComparingTo("95000");
			assertThat(limit.headroom()).isEqualByComparingTo("64000");
			assertThat(limit.limit()).isEqualByComparingTo("64000");
			assertThat(limit.binding()).isEqualTo(BorrowingLimit.Rule.TOTAL_CAP);
		}

		@Test
		void sharesCanBeBinding() {
			BorrowingLimit limit = calculator.limit(Money.of(10_000), Money.of(200_000), Money.ZERO);
			assertThat(limit.limit()).isEqualByComparingTo("30000");
			assertThat(limit.binding()).isEqualTo(BorrowingLimit.Rule.SHARES);
		}

		@Test
		void perLoanCapCanBeBinding() {
			// The mockup's case: 3 x 34,000 = 102,000, but half the pool is less.
			BorrowingLimit limit = calculator.limit(Money.of(34_000), Money.of(186_500), Money.ZERO);
			assertThat(limit.limit()).isEqualByComparingTo("93250");
			assertThat(limit.binding()).isEqualTo(BorrowingLimit.Rule.PER_LOAN_CAP);
		}

		@Test
		void headroomNeverGoesBelowZero() {
			BorrowingLimit limit = calculator.limit(Money.of(34_000), Money.of(100_000), Money.of(70_000));
			assertThat(limit.headroom()).isEqualByComparingTo("0");
			assertThat(limit.limit()).isEqualByComparingTo("0");
		}

		@Test
		void limitsRoundDownToWholeShillings() {
			BorrowingLimit limit = calculator.limit(Money.of(34_000), Money.of(100_001), Money.ZERO);
			assertThat(limit.perLoanCap()).isEqualByComparingTo("50000");
		}
	}

	@Test
	void guarantorsRequiredBySize() {
		assertThat(calculator.requiredGuarantors(Money.of(20_000))).isZero();
		assertThat(calculator.requiredGuarantors(Money.of(20_001))).isEqualTo(1);
		assertThat(calculator.requiredGuarantors(Money.of(35_000))).isEqualTo(1);
		assertThat(calculator.requiredGuarantors(Money.of(35_001))).isEqualTo(2);
	}

	@Nested
	class Repayments {

		private RepaymentSchedule schedule(long principal, Tenure tenure) {
			List<LoanCalculator.PlannedInstallment> plan = plan(principal, tenure);
			Loan loan = new Loan(null, Money.of(principal), tenure, calculator.ratePercent(tenure), DISBURSED,
					LoanKind.NEW, null, null, "M-Pesa", null);
			plan.forEach(loan::addInstallment);
			return RepaymentSchedule.replay(loan.getInstallments(), List.of());
		}

		@Test
		void oldestInstallmentFirstInterestBeforePrincipal() {
			// 15,000 over 3 months: 3 x 5,250, each 250 interest + 5,000 principal.
			RepaymentSchedule s = schedule(15_000, Tenure.THREE_MONTHS);
			RepaymentSchedule.Allocation first = s.apply(Money.of(1_000));
			assertThat(first.interest()).isEqualByComparingTo("250");
			assertThat(first.principal()).isEqualByComparingTo("750");
			assertThat(s.nextDue().installment().getNumber()).isEqualTo(1);

			RepaymentSchedule.Allocation second = s.apply(Money.of(4_500));
			// Finishes installment 1 (4,250 principal), then 250 interest of installment 2.
			assertThat(second.principal()).isEqualByComparingTo("4250");
			assertThat(second.interest()).isEqualByComparingTo("250");
			assertThat(s.nextDue().installment().getNumber()).isEqualTo(2);
		}

		@Test
		void payingOffEarlyIncludesAllTheInterest() {
			RepaymentSchedule s = schedule(15_000, Tenure.SIX_MONTHS);
			assertThat(s.remaining()).isEqualByComparingTo("16200");
			RepaymentSchedule.Allocation all = s.apply(Money.of(16_200));
			assertThat(all.interest()).isEqualByComparingTo("1200");
			assertThat(all.principal()).isEqualByComparingTo("15000");
			assertThat(s.isFullyPaid()).isTrue();
		}

		@Test
		void payingMoreThanTheBalanceIsRefused() {
			RepaymentSchedule s = schedule(15_000, Tenure.ONE_MONTH);
			assertThatThrownBy(() -> s.apply(Money.of(15_451))).isInstanceOf(LoanException.class);
		}
	}
}

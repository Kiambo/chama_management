package com.personal.chama.config;

import java.math.BigDecimal;
import java.time.YearMonth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Business settings that the group may change. Kept in configuration rather than in
 * code (brief section 3); defaults are in application.properties.
 */
@ConfigurationProperties(prefix = "chama")
public record ChamaProperties(Contribution contribution, YearMonth cutoverMonth, BigDecimal goalAmount, Loan loan) {

	/**
	 * @param mmfAmount the savings portion of the monthly contribution
	 * @param mgrAmount the merry-go-round portion
	 * @param dueDay    day of the month after which a payment counts as late
	 */
	public record Contribution(BigDecimal mmfAmount, BigDecimal mgrAmount, int dueDay) {

		public BigDecimal total() {
			return mmfAmount.add(mgrAmount);
		}
	}

	/**
	 * Loan policy settings (brief 4.4). Rates are percentages of the original principal,
	 * charged once. The rate in force is copied onto each loan when it is disbursed.
	 *
	 * @param sharesMultiple        borrowing limit as a multiple of the member's shares
	 * @param perLoanCapPercent     one loan can't exceed this % of the pool total
	 * @param totalCapPercent       all loans together can't exceed this % of the pool total
	 * @param guarantorMinShares    a guarantor needs at least these shares
	 * @param oneGuarantorAbove     loans above this need one guarantor
	 * @param twoGuarantorsAbove    loans above this need two guarantors
	 * @param dueSoonDays           an installment due within this many days is "due soon"
	 */
	public record Loan(BigDecimal sharesMultiple, BigDecimal perLoanCapPercent, BigDecimal totalCapPercent,
			BigDecimal guarantorMinShares, BigDecimal oneGuarantorAbove, BigDecimal twoGuarantorsAbove,
			int dueSoonDays, Rates rates) {
	}

	/** Flat interest, % of principal, by tenure tier. */
	public record Rates(BigDecimal twoWeeks, BigDecimal oneMonth, BigDecimal upToThreeMonths,
			BigDecimal upToSixMonths) {
	}
}

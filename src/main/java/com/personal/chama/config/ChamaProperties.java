package com.personal.chama.config;

import java.math.BigDecimal;
import java.time.YearMonth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Business settings that the group may change. Kept in configuration rather than in
 * code (brief section 3); defaults are in application.properties.
 */
@ConfigurationProperties(prefix = "chama")
public record ChamaProperties(Contribution contribution, YearMonth cutoverMonth, BigDecimal goalAmount) {

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
}

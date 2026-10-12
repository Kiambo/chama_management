package com.personal.chama.loan;

import java.math.BigDecimal;

import com.personal.chama.config.ChamaProperties;

/** The three borrowing limits and which one applies (brief: "show which rule is binding"). */
public record BorrowingLimit(BigDecimal byShares, BigDecimal perLoanCap, BigDecimal headroom,
		ChamaProperties.Loan settings) {

	public enum Rule {
		SHARES, PER_LOAN_CAP, TOTAL_CAP
	}

	public BigDecimal limit() {
		return byShares.min(perLoanCap).min(headroom);
	}

	/** The rule that sets the limit. On a tie, the first in the brief's order. */
	public Rule binding() {
		BigDecimal limit = limit();
		if (byShares.compareTo(limit) == 0) {
			return Rule.SHARES;
		}
		if (perLoanCap.compareTo(limit) == 0) {
			return Rule.PER_LOAN_CAP;
		}
		return Rule.TOTAL_CAP;
	}

	public String bindingDescription() {
		return switch (binding()) {
			case SHARES -> settings.sharesMultiple().stripTrailingZeros().toPlainString() + " x shares";
			case PER_LOAN_CAP -> settings.perLoanCapPercent().stripTrailingZeros().toPlainString() + "% of the pool per loan";
			case TOTAL_CAP -> "what's left under the " + settings.totalCapPercent().stripTrailingZeros().toPlainString()
					+ "% cap on all loans";
		};
	}
}

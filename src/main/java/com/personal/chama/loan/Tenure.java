package com.personal.chama.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.personal.chama.config.ChamaProperties;

/**
 * The tenures a loan can be taken for. Fixed choices only, so a term between tiers
 * can't be picked (decision L1). Loans of a month or less are one payment.
 */
public enum Tenure {

	TWO_WEEKS("2 weeks", 0),
	ONE_MONTH("1 month", 1),
	TWO_MONTHS("2 months", 2),
	THREE_MONTHS("3 months", 3),
	FOUR_MONTHS("4 months", 4),
	FIVE_MONTHS("5 months", 5),
	SIX_MONTHS("6 months", 6);

	private final String label;
	private final int months;

	Tenure(String label, int months) {
		this.label = label;
		this.months = months;
	}

	public String getLabel() {
		return label;
	}

	public int getMonths() {
		return months;
	}

	public boolean isSinglePayment() {
		return months <= 1;
	}

	public int installmentCount() {
		return isSinglePayment() ? 1 : months;
	}

	public String tierLabel() {
		return switch (this) {
			case TWO_WEEKS -> "Up to 2 weeks";
			case ONE_MONTH -> "Up to 1 month";
			case TWO_MONTHS, THREE_MONTHS -> "2 to 3 months";
			default -> "4 to 6 months";
		};
	}

	public BigDecimal ratePercent(ChamaProperties.Rates rates) {
		return switch (this) {
			case TWO_WEEKS -> rates.twoWeeks();
			case ONE_MONTH -> rates.oneMonth();
			case TWO_MONTHS, THREE_MONTHS -> rates.upToThreeMonths();
			default -> rates.upToSixMonths();
		};
	}

	/** When the loan ends: day 14 for two weeks, otherwise the same day N months later. */
	public LocalDate endDate(LocalDate disbursed) {
		return this == TWO_WEEKS ? disbursed.plusDays(14) : disbursed.plusMonths(months);
	}

	/** One payment at the end, or monthly from one month after disbursement (decision L4). */
	public List<LocalDate> dueDates(LocalDate disbursed) {
		if (isSinglePayment()) {
			return List.of(endDate(disbursed));
		}
		List<LocalDate> dates = new ArrayList<>();
		for (int i = 1; i <= months; i++) {
			dates.add(disbursed.plusMonths(i));
		}
		return dates;
	}
}

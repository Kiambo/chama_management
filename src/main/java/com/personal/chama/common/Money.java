package com.personal.chama.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** KES amounts: BigDecimal with 2 decimal places, never double (brief section 3). */
public final class Money {

	public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

	private Money() {
	}

	public static BigDecimal of(long shillings) {
		return BigDecimal.valueOf(shillings).setScale(2);
	}

	public static BigDecimal of(BigDecimal amount) {
		return amount.setScale(2, RoundingMode.UNNECESSARY);
	}

	public static BigDecimal orZero(BigDecimal amount) {
		return amount == null ? ZERO : amount.setScale(2);
	}

	public static boolean isPositive(BigDecimal amount) {
		return amount.signum() > 0;
	}

	/** "186,500" - cents only shown when there are any, e.g. "1,500.50". */
	public static String format(BigDecimal amount) {
		if (amount == null) {
			return "";
		}
		boolean whole = amount.stripTrailingZeros().scale() <= 0;
		DecimalFormat format = new DecimalFormat(whole ? "#,##0" : "#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
		return format.format(amount);
	}
}

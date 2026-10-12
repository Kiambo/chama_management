package com.personal.chama.web;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.personal.chama.common.Money;

/** Display helpers for templates: ${@fmt.kes(amount)}, ${@fmt.month(m)}. */
@Component("fmt")
public class Formats {

	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

	public String kes(BigDecimal amount) {
		return "KES " + Money.format(amount);
	}

	public String amount(BigDecimal amount) {
		return Money.format(amount);
	}

	/** A number without trailing zeros, e.g. 8 rather than 8.0000. */
	public String plain(BigDecimal value) {
		return value == null ? "" : value.stripTrailingZeros().toPlainString();
	}

	public String month(YearMonth month) {
		return month == null ? "" : MONTH.format(month);
	}
}

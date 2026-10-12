package com.personal.chama.common;

import java.time.LocalDate;
import java.time.YearMonth;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores a month as the first day of that month in a DATE column. */
@Converter(autoApply = true)
public class YearMonthConverter implements AttributeConverter<YearMonth, LocalDate> {

	@Override
	public LocalDate convertToDatabaseColumn(YearMonth month) {
		return month == null ? null : month.atDay(1);
	}

	@Override
	public YearMonth convertToEntityAttribute(LocalDate date) {
		return date == null ? null : YearMonth.from(date);
	}
}

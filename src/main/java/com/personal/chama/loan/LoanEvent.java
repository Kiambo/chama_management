package com.personal.chama.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One line in a loan's history, for the member page: what happened, when, and how much.
 *
 * @param tone     colour hint: green (money in), amber (needs attention), red (missed, loss), grey (neutral)
 * @param reversed a payment that was later corrected; shown struck through
 */
public record LoanEvent(LocalDate date, String label, BigDecimal amount, String detail, String tone,
		boolean reversed) {
}

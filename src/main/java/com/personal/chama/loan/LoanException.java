package com.personal.chama.loan;

/** A loan rule was broken. The message is shown to the recorder as is. */
public class LoanException extends RuntimeException {

	public LoanException(String message) {
		super(message);
	}
}

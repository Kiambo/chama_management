package com.personal.chama.decision;

/** A decision couldn't be recorded. The message is shown to the recorder as is. */
public class DecisionException extends RuntimeException {

	public DecisionException(String message) {
		super(message);
	}
}

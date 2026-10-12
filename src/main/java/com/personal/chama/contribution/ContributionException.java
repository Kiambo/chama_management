package com.personal.chama.contribution;

/** A contribution rule was broken. The message is shown to the treasurer as is. */
public class ContributionException extends RuntimeException {

	public ContributionException(String message) {
		super(message);
	}
}

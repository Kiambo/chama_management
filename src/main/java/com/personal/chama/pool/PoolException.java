package com.personal.chama.pool;

/** A pool rule was broken. The message is shown to the treasurer as is. */
public class PoolException extends RuntimeException {

	public PoolException(String message) {
		super(message);
	}
}

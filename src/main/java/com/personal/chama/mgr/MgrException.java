package com.personal.chama.mgr;

/** A merry-go-round rule was broken. The message is shown to the recorder as is. */
public class MgrException extends RuntimeException {

	public MgrException(String message) {
		super(message);
	}
}

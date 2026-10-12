package com.personal.chama.user;

/** A profile change was refused. The message is shown to the user as is. */
public class ProfileException extends RuntimeException {

	public ProfileException(String message) {
		super(message);
	}
}

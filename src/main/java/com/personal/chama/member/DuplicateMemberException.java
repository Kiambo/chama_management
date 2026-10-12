package com.personal.chama.member;

public class DuplicateMemberException extends RuntimeException {

	public DuplicateMemberException(String fullName) {
		super("A member called " + fullName + " already exists");
	}
}

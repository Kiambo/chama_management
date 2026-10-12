package com.personal.chama.user;

import java.time.Clock;
import java.time.Instant;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.chama.member.Member;
import com.personal.chama.member.MemberRepository;

/** The logged-in user's own details: linked member, email, phone and password. */
@Service
@Transactional(readOnly = true)
public class ProfileService {

	private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
	private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ]{7,20}$");

	private final AppUserRepository users;
	private final MemberRepository members;
	private final PasswordEncoder passwordEncoder;
	private final Clock clock;

	public ProfileService(AppUserRepository users, MemberRepository members, PasswordEncoder passwordEncoder,
			Clock clock) {
		this.users = users;
		this.members = members;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	public AppUser get(String username) {
		return users.findByUsername(username).orElseThrow(() -> new ProfileException("No such user."));
	}

	/** Links the login to the member it belongs to, so the greeting and "my page" use it. */
	@Transactional
	public void linkMember(String username, Long memberId) {
		AppUser user = get(username);
		Member member = memberId == null ? null
				: members.findById(memberId).orElseThrow(() -> new ProfileException("No such member."));
		user.setMember(member);
	}

	@Transactional
	public void updateContact(String username, String email, String phone) {
		String e = blankToNull(email);
		String p = blankToNull(phone);
		if (e != null && (e.length() > 254 || !EMAIL.matcher(e).matches())) {
			throw new ProfileException("That doesn't look like an email address.");
		}
		if (p != null && !PHONE.matcher(p).matches()) {
			throw new ProfileException("Enter the phone number with digits only, e.g. +254 712 345678.");
		}
		get(username).setContact(e, p);
	}

	@Transactional
	public void changePassword(String username, String current, String newPassword, String repeat) {
		AppUser user = get(username);
		if (current == null || !passwordEncoder.matches(current, user.getPasswordHash())) {
			throw new ProfileException("The current password is wrong.");
		}
		if (newPassword == null || newPassword.length() < AdminSeeder.MIN_PASSWORD_LENGTH) {
			throw new ProfileException("The new password must be at least " + AdminSeeder.MIN_PASSWORD_LENGTH
					+ " characters.");
		}
		if (!newPassword.equals(repeat)) {
			throw new ProfileException("The two new passwords don't match.");
		}
		if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
			throw new ProfileException("The new password is the same as the current one.");
		}
		user.changePassword(passwordEncoder.encode(newPassword), Instant.now(clock));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}

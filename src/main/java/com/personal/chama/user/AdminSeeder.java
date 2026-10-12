package com.personal.chama.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first recorder account from ADMIN_USERNAME / ADMIN_PASSWORD when the
 * database has no users. Does nothing once any user exists, so changing the
 * environment variables later never overwrites a real password.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

	static final int MIN_PASSWORD_LENGTH = 12;

	private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

	private final AppUserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final String username;
	private final String password;

	public AdminSeeder(AppUserRepository users, PasswordEncoder passwordEncoder,
			@Value("${chama.admin.username}") String username,
			@Value("${chama.admin.password}") String password) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.username = username;
		this.password = password;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (users.count() > 0) {
			return;
		}
		if (username.isBlank() || password.isBlank()) {
			log.warn("No users exist and ADMIN_USERNAME / ADMIN_PASSWORD are not set: nobody can log in.");
			return;
		}
		if (password.length() < MIN_PASSWORD_LENGTH) {
			throw new IllegalStateException(
					"ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters");
		}
		users.save(new AppUser(username, passwordEncoder.encode(password), Role.RECORDER));
		log.info("Created recorder account '{}'", username);
	}
}

package com.personal.chama.config;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
@EnableJpaAuditing
public class AppConfig {

	/** All "today" and "this month" logic uses this clock, so it follows chama time. */
	@Bean
	Clock clock(@Value("${chama.timezone}") String timezone) {
		return Clock.system(ZoneId.of(timezone));
	}

	/** Fills created_by / updated_by with the logged-in user, or "system" outside a request. */
	@Bean
	AuditorAware<String> auditorAware() {
		return () -> {
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
				return Optional.of("system");
			}
			return Optional.of(auth.getName());
		};
	}
}

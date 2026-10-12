package com.personal.chama.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.personal.chama.IntegrationTest;
import com.personal.chama.member.Member;
import com.personal.chama.member.MemberRepository;

/** Uses its own login, so the shared test treasurer's password never changes. */
@IntegrationTest
@WithMockUser(username = ProfileIntegrationTest.USER, roles = "TREASURER")
class ProfileIntegrationTest {

	static final String USER = "profile-tester";
	static final String PASSWORD = "first-password-123";

	@Autowired
	MockMvc mvc;
	@Autowired
	ProfileService profiles;
	@Autowired
	AppUserRepository users;
	@Autowired
	MemberRepository members;
	@Autowired
	PasswordEncoder encoder;

	@BeforeEach
	void user() {
		users.findByUsername(USER).ifPresent(users::delete);
		users.save(new AppUser(USER, encoder.encode(PASSWORD), Role.TREASURER));
	}

	@Test
	void changingThePassword() {
		assertThatThrownBy(() -> profiles.changePassword(USER, "wrong", "a-new-password-1", "a-new-password-1"))
				.isInstanceOf(ProfileException.class).hasMessageContaining("current password is wrong");
		assertThatThrownBy(() -> profiles.changePassword(USER, PASSWORD, "short", "short"))
				.isInstanceOf(ProfileException.class).hasMessageContaining("at least 12");
		assertThatThrownBy(() -> profiles.changePassword(USER, PASSWORD, "a-new-password-1", "a-new-password-2"))
				.isInstanceOf(ProfileException.class).hasMessageContaining("don't match");

		profiles.changePassword(USER, PASSWORD, "a-new-password-1", "a-new-password-1");

		AppUser user = users.findByUsername(USER).orElseThrow();
		assertThat(encoder.matches("a-new-password-1", user.getPasswordHash())).isTrue();
		assertThat(user.getPasswordChangedAt()).isNotNull();
	}

	@Test
	void contactDetailsAreChecked() {
		assertThatThrownBy(() -> profiles.updateContact(USER, "not-an-email", null)).isInstanceOf(ProfileException.class);
		assertThatThrownBy(() -> profiles.updateContact(USER, null, "call me")).isInstanceOf(ProfileException.class);
		profiles.updateContact(USER, "treasurer@example.com", "+254 712 345678");
		AppUser user = users.findByUsername(USER).orElseThrow();
		assertThat(user.getEmail()).isEqualTo("treasurer@example.com");
		assertThat(user.getPhone()).isEqualTo("+254 712 345678");
	}

	@Test
	void linkingTheLoginToAMemberChangesTheGreeting() throws Exception {
		Member me = members.save(new Member("Wanjiku Example", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1)));
		mvc.perform(post("/profile/member").with(csrf()).param("memberId", me.getId().toString()));
		mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(Matchers.containsString(", Wanjiku</h1>")));
		mvc.perform(get("/profile")).andExpect(status().isOk()).andExpect(content().string(Matchers.containsString("Wanjiku Example")));
	}
}

package com.personal.chama.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.personal.chama.IntegrationTest;

@IntegrationTest
class SecurityIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Test
	void pagesRequireLogin() throws Exception {
		mvc.perform(get("/")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
		mvc.perform(get("/members")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
	}

	@Test
	void loginPageAndStylesheetArePublic() throws Exception {
		mvc.perform(get("/login")).andExpect(status().isOk());
		mvc.perform(get("/css/app.css")).andExpect(status().isOk());
	}

	@Test
	void seededRecorderCanLogIn() throws Exception {
		mvc.perform(formLogin().user(IntegrationTest.RECORDER).password(IntegrationTest.RECORDER_PASSWORD))
				.andExpect(authenticated().withUsername(IntegrationTest.RECORDER).withRoles("RECORDER"))
				.andExpect(redirectedUrl("/"));
	}

	@Test
	void wrongPasswordIsRefused() throws Exception {
		mvc.perform(formLogin().user(IntegrationTest.RECORDER).password("not-the-password"))
				.andExpect(unauthenticated())
				.andExpect(redirectedUrl("/login?error"));
	}

	@Test
	void readOnlyMemberRoleCannotChangeData() throws Exception {
		mvc.perform(post("/members").with(user("viewer").roles("MEMBER")).with(csrf())
				.param("fullName", "Should Not Save").param("joinDate", "2025-03-01"))
				.andExpect(status().isForbidden());
	}

	@Test
	void formsWithoutCsrfTokenAreRejected() throws Exception {
		mvc.perform(post("/members").with(user("recorder").roles("RECORDER"))
				.param("fullName", "No Token").param("joinDate", "2025-03-01"))
				.andExpect(status().isForbidden());
	}
}

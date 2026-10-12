package com.personal.chama.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.personal.chama.IntegrationTest;

@IntegrationTest
class DashboardIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Test
	@WithMockUser(username = "grace", roles = "TREASURER")
	void greetsWithCapitalisedNameAndShowsTiles() throws Exception {
		mvc.perform(get("/"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(", Grace</h1>")))
				.andExpect(content().string(containsString("est. 2024")))
				.andExpect(content().string(containsString("Merry-go-round rotation")))
				.andExpect(content().string(containsString("aria-current=\"page\"")));
	}
}

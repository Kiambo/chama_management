package com.personal.chama.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.personal.chama.IntegrationTest;

@IntegrationTest
@WithMockUser(username = "recorder-a", roles = "RECORDER")
class MemberIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	MemberRepository members;

	/** Tests share one database, so each uses its own name. */
	private static String uniqueName() {
		return "Test Member " + UUID.randomUUID().toString().substring(0, 8);
	}

	private Member findByName(String name) {
		return members.findAll().stream()
				.filter(m -> m.getFullName().equals(name))
				.findFirst()
				.orElseThrow();
	}

	@Test
	void addedMemberIsSavedWithWhoRecordedIt() throws Exception {
		String name = uniqueName();

		mvc.perform(post("/members").with(csrf())
				.param("fullName", "  " + name + "  ")
				.param("joinDate", "2026-09-01")
				.param("mgrStartDate", "2027-01-01"))
				.andExpect(redirectedUrl("/members"));

		Member saved = findByName(name);
		assertThat(saved.getJoinDate()).isEqualTo(LocalDate.of(2026, 9, 1));
		assertThat(saved.getMgrStartDate()).isEqualTo(LocalDate.of(2027, 1, 1));
		assertThat(saved.isActive()).isTrue();
		assertThat(saved.getCreatedBy()).isEqualTo("recorder-a");
		assertThat(saved.getCreatedAt()).isNotNull();

		mvc.perform(get("/members")).andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString(name)));
	}

	@Test
	void memberCanBeInSavingsWithoutMgr() throws Exception {
		String name = uniqueName();

		mvc.perform(post("/members").with(csrf()).param("fullName", name).param("joinDate", "2026-09-01"))
				.andExpect(redirectedUrl("/members"));

		assertThat(findByName(name).getMgrStartDate()).isNull();
	}

	@Test
	void nameAndJoinDateAreRequired() throws Exception {
		long before = members.count();

		mvc.perform(post("/members").with(csrf()).param("fullName", " ").param("joinDate", ""))
				.andExpect(status().isOk())
				.andExpect(view().name("members/form"))
				.andExpect(model().attributeHasFieldErrors("form", "fullName", "joinDate"));

		assertThat(members.count()).isEqualTo(before);
	}

	@Test
	void mgrStartCannotBeBeforeJoinDate() throws Exception {
		mvc.perform(post("/members").with(csrf())
				.param("fullName", uniqueName())
				.param("joinDate", "2026-09-01")
				.param("mgrStartDate", "2026-08-01"))
				.andExpect(view().name("members/form"))
				.andExpect(model().attributeHasFieldErrors("form", "mgrStartDateValid"));
	}

	@Test
	void duplicateNamesAreRejectedIgnoringCase() throws Exception {
		String name = uniqueName();
		mvc.perform(post("/members").with(csrf()).param("fullName", name).param("joinDate", "2025-03-01"));

		mvc.perform(post("/members").with(csrf()).param("fullName", name.toUpperCase()).param("joinDate", "2025-03-01"))
				.andExpect(view().name("members/form"))
				.andExpect(model().attributeHasFieldErrorCode("form", "fullName", "duplicate"));
	}

	@Test
	void memberCanBeEdited() throws Exception {
		String name = uniqueName();
		mvc.perform(post("/members").with(csrf()).param("fullName", name).param("joinDate", "2025-03-01"));
		Long id = findByName(name).getId();

		mvc.perform(get("/members/{id}/edit", id)).andExpect(status().isOk());
		mvc.perform(post("/members/{id}", id).with(csrf())
				.param("fullName", name + " Edited")
				.param("joinDate", "2025-04-01")
				.param("mgrStartDate", "2025-04-01"))
				.andExpect(redirectedUrl("/members"));

		Member edited = members.findById(id).orElseThrow();
		assertThat(edited.getFullName()).isEqualTo(name + " Edited");
		assertThat(edited.getJoinDate()).isEqualTo(LocalDate.of(2025, 4, 1));
	}

	@Test
	void memberIsDeactivatedNotDeleted() throws Exception {
		String name = uniqueName();
		mvc.perform(post("/members").with(csrf()).param("fullName", name).param("joinDate", "2025-03-01"));
		Long id = findByName(name).getId();

		mvc.perform(post("/members/{id}/deactivate", id).with(csrf())).andExpect(redirectedUrl("/members"));
		assertThat(members.findById(id)).get().extracting(Member::isActive).isEqualTo(false);

		mvc.perform(post("/members/{id}/activate", id).with(csrf())).andExpect(redirectedUrl("/members"));
		assertThat(members.findById(id)).get().extracting(Member::isActive).isEqualTo(true);
	}

	@Test
	void unknownMemberIsNotFound() throws Exception {
		mvc.perform(get("/members/{id}/edit", 999_999)).andExpect(status().isNotFound());
	}
}

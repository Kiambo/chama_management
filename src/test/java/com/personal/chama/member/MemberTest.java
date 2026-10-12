package com.personal.chama.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class MemberTest {

	private static final LocalDate JOINED = LocalDate.of(2025, 3, 1);

	@Test
	void initialsUseFirstAndLastName() {
		assertThat(new Member("grace wanjiru mwangi", JOINED, null).getInitials()).isEqualTo("GM");
		assertThat(new Member("Grace", JOINED, null).getInitials()).isEqualTo("G");
	}

	@Test
	void firstName() {
		assertThat(new Member("Grace Mwangi", JOINED, null).getFirstName()).isEqualTo("Grace");
	}

	@Test
	void nameIsTrimmed() {
		assertThat(new Member("  Grace Mwangi ", JOINED, null).getFullName()).isEqualTo("Grace Mwangi");
	}

	@Test
	void mgrStartCannotBeBeforeJoining() {
		assertThatThrownBy(() -> new Member("Grace", JOINED, JOINED.minusDays(1)))
				.isInstanceOf(IllegalArgumentException.class);
	}
}

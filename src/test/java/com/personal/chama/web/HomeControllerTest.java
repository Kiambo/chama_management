package com.personal.chama.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class HomeControllerTest {

	@Test
	void greetingFollowsTimeOfDay() {
		assertThat(HomeController.greeting(LocalTime.of(7, 30))).isEqualTo("Good morning");
		assertThat(HomeController.greeting(LocalTime.NOON)).isEqualTo("Good afternoon");
		assertThat(HomeController.greeting(LocalTime.of(16, 59))).isEqualTo("Good afternoon");
		assertThat(HomeController.greeting(LocalTime.of(17, 0))).isEqualTo("Good evening");
	}

	@Test
	void namesAreCapitalisedForDisplay() {
		assertThat(HomeController.capitalizeWords("lilian")).isEqualTo("Lilian");
		assertThat(HomeController.capitalizeWords("mary anne")).isEqualTo("Mary Anne");
		assertThat(HomeController.capitalizeWords("mary-anne")).isEqualTo("Mary-Anne");
		assertThat(HomeController.capitalizeWords("McDonald")).isEqualTo("McDonald");
		assertThat(HomeController.capitalizeWords("")).isEmpty();
	}
}

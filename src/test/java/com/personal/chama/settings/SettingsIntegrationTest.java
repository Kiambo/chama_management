package com.personal.chama.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.personal.chama.IntegrationTest;
import com.personal.chama.contribution.ContributionRules;
import com.personal.chama.decision.DecisionException;
import com.personal.chama.decision.DecisionInput;
import com.personal.chama.loan.Loan;
import com.personal.chama.loan.LoanCalculator;
import com.personal.chama.loan.LoanForm;
import com.personal.chama.loan.LoanKind;
import com.personal.chama.loan.LoanService;
import com.personal.chama.loan.Tenure;
import com.personal.chama.member.Member;
import com.personal.chama.member.MemberRepository;

/**
 * Settings changes apply to everything after their date, so this class gets its own
 * context and database (the different property makes Spring start a separate one) and
 * can't disturb other tests.
 */
@IntegrationTest
@TestPropertySource(properties = "chama.established-year=2023")
@WithMockUser(username = "treasurer-s", roles = "TREASURER")
class SettingsIntegrationTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	SettingsService settings;
	@Autowired
	PaymentMethodService methods;
	@Autowired
	ContributionRules rules;
	@Autowired
	LoanCalculator calculator;
	@Autowired
	LoanService loans;
	@Autowired
	MemberRepository members;

	private static DecisionInput vote() {
		return DecisionInput.of(null, "Meeting, all in favour", "test");
	}

	private static LocalDate today() {
		return LocalDate.now();
	}

	@Test
	void contributionChangesStartOnTheFirstOfAComingMonthAndNeverRewriteThePast() {
		YearMonth thisMonth = YearMonth.now();
		YearMonth next = thisMonth.plusMonths(1);
		Member m = members.save(new Member("Settings Member", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1)));

		assertThatThrownBy(() -> settings.change(Map.of(SettingKey.MMF_AMOUNT, BigDecimal.valueOf(2500)), today(), vote()))
				.isInstanceOf(SettingsException.class).hasMessageContaining("1st of a month");

		settings.change(Map.of(SettingKey.MMF_AMOUNT, BigDecimal.valueOf(2500)), next.atDay(1), vote());

		assertThat(rules.due(m, thisMonth, null).mmf()).isEqualByComparingTo("2000");
		assertThat(rules.due(m, next, null).mmf()).isEqualByComparingTo("2500");
		assertThat(rules.due(m, thisMonth.minusMonths(3), null).mmf()).isEqualByComparingTo("2000");
	}

	@Test
	void aNewRateAppliesToNewLoansOnly() {
		Member borrower = members.save(new Member("Rate Member", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 1)));
		LoanForm form = new LoanForm();
		form.setBorrowerId(borrower.getId());
		form.setAmount(BigDecimal.valueOf(10_000));
		form.setTenure(Tenure.ONE_MONTH);
		form.setDisbursedDate(today());
		form.setMethod("M-Pesa");
		form.setKind(LoanKind.EXISTING_AS_AGREED);
		form.setNote("test");
		Loan before = loans.disburse(form);

		settings.change(Map.of(SettingKey.RATE_ONE_MONTH, BigDecimal.valueOf(4)), today(), vote());

		assertThat(calculator.ratePercent(Tenure.ONE_MONTH)).isEqualByComparingTo("4");
		assertThat(loans.view(before.getId()).loan().getRatePercent()).isEqualByComparingTo("3");
		assertThat(loans.view(before.getId()).loan().getInterest()).isEqualByComparingTo("300");
	}

	@Test
	void aLaterChangeDoesntApplyBeforeItsDate() {
		LocalDate later = today().plusDays(10);
		settings.change(Map.of(SettingKey.GRACE_DAYS, BigDecimal.valueOf(10)), later, vote());
		assertThat(settings.asOf(today()).loan().graceDays()).isEqualTo(5);
		assertThat(settings.asOf(later).loan().graceDays()).isEqualTo(10);
	}

	@Test
	void changesNeedAGroupDecisionAndAValidValue() {
		assertThatThrownBy(() -> settings.change(Map.of(SettingKey.LATE_FEE_PERCENT, BigDecimal.valueOf(6)), today(),
				DecisionInput.of(null, " ", null))).isInstanceOf(DecisionException.class);
		assertThatThrownBy(() -> settings.change(Map.of(SettingKey.LATE_FEE_PERCENT, BigDecimal.valueOf(150)), today(), vote()))
				.isInstanceOf(SettingsException.class).hasMessageContaining("between 0 and 100");
		assertThatThrownBy(() -> settings.change(Map.of(SettingKey.TWO_GUARANTORS_ABOVE, BigDecimal.valueOf(10_000)), today(), vote()))
				.isInstanceOf(SettingsException.class).hasMessageContaining("2 guarantors");
		assertThatThrownBy(() -> settings.change(Map.of(SettingKey.GOAL_AMOUNT, settings.current().goalAmount()), today(), vote()))
				.isInstanceOf(SettingsException.class).hasMessageContaining("Nothing was changed");
		assertThatThrownBy(() -> settings.change(Map.of(SettingKey.GOAL_AMOUNT, BigDecimal.valueOf(300_000)),
				today().minusDays(1), vote())).isInstanceOf(SettingsException.class).hasMessageContaining("never apply to the past");
	}

	@Test
	void paymentMethodsCanBeAddedAndHidden() {
		methods.add("Equity Bank");
		assertThat(methods.activeNames()).contains("Equity Bank");
		assertThatThrownBy(() -> methods.add("equity bank")).isInstanceOf(SettingsException.class);
		PaymentMethod equity = methods.all().stream().filter(m -> m.getName().equals("Equity Bank")).findFirst().orElseThrow();
		methods.setActive(equity.getId(), false);
		assertThat(methods.activeNames()).doesNotContain("Equity Bank");
	}

	@Test
	void settingsPage() throws Exception {
		mvc.perform(get("/settings")).andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("Interest: up to 1 month")));
		mvc.perform(multipart("/settings").with(csrf())
				.param("v_GOAL_AMOUNT", "275000").param("effectiveFrom", today().toString())
				.param("howAgreed", "WhatsApp vote, 7 of 7"))
				.andExpect(flash().attribute("message", Matchers.containsString("1 setting changed")));
		assertThat(settings.current().goalAmount()).isEqualByComparingTo("275000");
		mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(Matchers.containsString("275,000")));
	}
}

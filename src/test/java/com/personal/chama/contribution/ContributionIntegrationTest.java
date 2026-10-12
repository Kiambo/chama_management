package com.personal.chama.contribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.personal.chama.IntegrationTest;
import com.personal.chama.common.Money;
import com.personal.chama.ledger.LedgerEntry;
import com.personal.chama.ledger.LedgerEntryRepository;
import com.personal.chama.ledger.LedgerEntryType;
import com.personal.chama.ledger.LedgerService;
import com.personal.chama.member.Member;
import com.personal.chama.member.MemberRepository;

/**
 * Tests share one database with other test classes, so balances are checked as the
 * change caused by each test, never as absolute totals.
 */
@IntegrationTest
@WithMockUser(username = "recorder-b", roles = "RECORDER")
class ContributionIntegrationTest {

	private static final YearMonth OCT = YearMonth.of(2026, 10);
	private static final LocalDate PAID_ON_TIME = LocalDate.of(2026, 10, 5);

	@Autowired
	MockMvc mvc;
	@Autowired
	ContributionService contributions;
	@Autowired
	LedgerService ledger;
	@Autowired
	LedgerEntryRepository ledgerEntries;
	@Autowired
	MemberRepository members;
	@Autowired
	JdbcTemplate jdbc;

	private Member member;

	@BeforeEach
	void newMember() {
		member = members.save(new Member("Contrib " + UUID.randomUUID().toString().substring(0, 8),
				LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 1)));
	}

	private ContributionForm form(long amount) {
		ContributionForm form = new ContributionForm();
		form.setMemberId(member.getId());
		form.setMonth(OCT);
		form.setAmount(Money.of(amount));
		form.setPaidDate(PAID_ON_TIME);
		form.setMethod("M-Pesa");
		form.setReference("QWE123");
		return form;
	}

	@Test
	void standardContributionIsSplitIntoTwoLedgerEntries() {
		BigDecimal poolBefore = ledger.mmfBalance();
		BigDecimal mgrBefore = ledger.mgrCollectedFor(OCT);

		Contribution saved = contributions.record(form(3500));

		assertThat(saved.getMmfPortion()).isEqualByComparingTo("2000");
		assertThat(saved.getMgrPortion()).isEqualByComparingTo("1500");
		assertThat(saved.getRecordedBy()).isEqualTo("recorder-b");
		List<LedgerEntry> entries = ledgerEntries.findByContributionIdAndReversesIdIsNull(saved.getId());
		assertThat(entries).extracting(LedgerEntry::getType)
				.containsExactlyInAnyOrder(LedgerEntryType.CONTRIBUTION_MMF, LedgerEntryType.CONTRIBUTION_MGR);

		assertThat(ledger.sharesOf(member.getId())).isEqualByComparingTo("2000");
		assertThat(ledger.mmfBalance().subtract(poolBefore)).isEqualByComparingTo("2000");
		assertThat(ledger.mgrCollectedFor(OCT).subtract(mgrBefore)).isEqualByComparingTo("1500");
		assertThat(contributions.memberMonth(member.getId(), OCT).status()).isEqualTo(MonthStatus.PAID);
	}

	@Test
	void monthCanBePaidInTwoGoes() {
		contributions.record(form(2000));
		assertThat(contributions.memberMonth(member.getId(), OCT).status()).isEqualTo(MonthStatus.PARTLY_PAID);

		Contribution second = contributions.record(form(1500));

		assertThat(second.getMgrPortion()).isEqualByComparingTo("1500");
		assertThat(contributions.memberMonth(member.getId(), OCT).status()).isEqualTo(MonthStatus.PAID);
	}

	@Test
	void overpaymentIsRefusedAndNothingIsSaved() throws Exception {
		BigDecimal poolBefore = ledger.mmfBalance();

		mvc.perform(post("/contributions").with(csrf())
				.param("memberId", member.getId().toString())
				.param("month", "2026-10")
				.param("amount", "7000")
				.param("paidDate", PAID_ON_TIME.toString())
				.param("method", "M-Pesa"))
				.andExpect(status().isOk())
				.andExpect(view().name("contributions/form"))
				.andExpect(content().string(Matchers.containsString("more than is owed")));

		assertThat(ledger.mmfBalance()).isEqualByComparingTo(poolBefore);
		assertThat(ledger.sharesOf(member.getId())).isEqualByComparingTo("0");
	}

	@Test
	void recordingThroughTheFormRedirectsToTheMonth() throws Exception {
		mvc.perform(post("/contributions").with(csrf())
				.param("memberId", member.getId().toString())
				.param("month", "2026-10")
				.param("amount", "3500")
				.param("paidDate", PAID_ON_TIME.toString())
				.param("method", "PayPal"))
				.andExpect(redirectedUrl("/contributions?month=2026-10"));

		assertThat(ledger.sharesOf(member.getId())).isEqualByComparingTo("2000");
	}

	@Test
	void futurePaymentDateIsRefused() {
		ContributionForm form = form(3500);
		form.setPaidDate(LocalDate.now().plusDays(5));
		assertThatThrownBy(() -> contributions.record(form)).isInstanceOf(ContributionException.class);
	}

	@Test
	void correctionReversesEntriesAndKeepsHistory() {
		BigDecimal poolBefore = ledger.mmfBalance();
		Contribution saved = contributions.record(form(3500));
		long entriesBefore = ledgerEntries.count();

		contributions.voidContribution(saved.getId(), "Wrong member");

		// Two reversing entries were added; nothing was removed.
		assertThat(ledgerEntries.count()).isEqualTo(entriesBefore + 2);
		assertThat(ledger.mmfBalance()).isEqualByComparingTo(poolBefore);
		assertThat(ledger.sharesOf(member.getId())).isEqualByComparingTo("0");
		assertThat(contributions.memberMonth(member.getId(), OCT).paid().total()).isEqualByComparingTo("0");
		assertThat(contributions.get(saved.getId()).isVoided()).isTrue();

		assertThatThrownBy(() -> contributions.voidContribution(saved.getId(), "again"))
				.isInstanceOf(ContributionException.class);
	}

	@Test
	void databaseRefusesToChangeOrDeleteLedgerEntries() {
		Contribution saved = contributions.record(form(3500));
		Long entryId = ledgerEntries.findByContributionIdAndReversesIdIsNull(saved.getId()).get(0).getId();

		assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entry SET amount = 1 WHERE id = ?", entryId))
				.hasMessageContaining("append-only");
		assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger_entry WHERE id = ?", entryId))
				.hasMessageContaining("append-only");
	}

	@Test
	void optOutReducesWhatIsDue() {
		contributions.setOptOut(member.getId(), OCT, false, true, "Short this month");

		MemberMonth month = contributions.memberMonth(member.getId(), OCT);
		assertThat(month.due().total()).isEqualByComparingTo("2000");

		contributions.record(form(2000));
		assertThat(contributions.memberMonth(member.getId(), OCT).status()).isEqualTo(MonthStatus.PAID);
	}

	@Test
	void optingOutOfBothShowsOptedOut() {
		contributions.setOptOut(member.getId(), OCT, true, true, null);
		assertThat(contributions.memberMonth(member.getId(), OCT).status()).isEqualTo(MonthStatus.OPTED_OUT);
	}

	@Test
	void cannotOptOutOfAPartAlreadyPaid() {
		contributions.record(form(2000));
		assertThatThrownBy(() -> contributions.setOptOut(member.getId(), OCT, true, false, null))
				.isInstanceOf(ContributionException.class)
				.hasMessageContaining("already paid");
	}

	@Test
	void newMemberBackPaysSavingsOnly() {
		Member newcomer = members.save(new Member("Newcomer " + UUID.randomUUID().toString().substring(0, 8),
				LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 1)));
		ContributionForm form = form(2000);
		form.setMemberId(newcomer.getId());
		form.setMonth(YearMonth.of(2026, 9));
		form.setAgreedBackpay(true);

		Contribution saved = contributions.record(form);

		assertThat(saved.getMmfPortion()).isEqualByComparingTo("2000");
		assertThat(saved.getMgrPortion()).isEqualByComparingTo("0");
		assertThat(ledgerEntries.findByContributionIdAndReversesIdIsNull(saved.getId())).hasSize(1);
		assertThat(contributions.memberMonth(newcomer.getId(), YearMonth.of(2026, 9)).status()).isEqualTo(MonthStatus.PAID);
	}

	@Test
	void cannotPayForAMonthBeforeJoining() {
		ContributionForm form = form(3500);
		form.setMonth(YearMonth.of(2025, 1));
		assertThatThrownBy(() -> contributions.record(form))
				.isInstanceOf(ContributionException.class)
				.hasMessageContaining("had not joined");
	}

	@Test
	void pagesRender() throws Exception {
		contributions.record(form(3500));
		mvc.perform(get("/contributions").param("month", "2026-10")).andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString(member.getFullName())));
		mvc.perform(get("/contributions/new").param("memberId", member.getId().toString()).param("month", "2026-10"))
				.andExpect(status().isOk());
		mvc.perform(get("/members/{id}", member.getId())).andExpect(status().isOk())
				.andExpect(model().attribute("shares", Matchers.comparesEqualTo(new BigDecimal("2000.00"))));
		mvc.perform(get("/ledger")).andExpect(status().isOk());
		mvc.perform(get("/ledger").param("memberId", member.getId().toString())).andExpect(status().isOk());
		mvc.perform(get("/")).andExpect(status().isOk());
	}
}

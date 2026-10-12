package com.personal.chama.mgr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.personal.chama.IntegrationTest;
import com.personal.chama.common.Money;
import com.personal.chama.contribution.ContributionException;
import com.personal.chama.contribution.ContributionForm;
import com.personal.chama.contribution.ContributionService;
import com.personal.chama.contribution.MonthStatus;
import com.personal.chama.decision.DecisionInput;
import com.personal.chama.ledger.LedgerService;
import com.personal.chama.member.Member;
import com.personal.chama.member.MemberRepository;

/**
 * A whole round of the merry-go-round, played through past months. The rotation is
 * shared across the database, so this class gets its own context and database (the
 * different cut-over makes Spring start a separate one), with the cut-over in January
 * 2020 so those months can be paid out.
 */
@IntegrationTest
@TestPropertySource(properties = "chama.cutover-month=2020-01")
@WithMockUser(username = "recorder-f", roles = "TREASURER")
class MgrIntegrationTest {

	private static final YearMonth JAN = YearMonth.of(2020, 1);
	private static final YearMonth FEB = YearMonth.of(2020, 2);
	private static final YearMonth MAR = YearMonth.of(2020, 3);
	private static final YearMonth APR = YearMonth.of(2020, 4);

	@Autowired
	MockMvc mvc;
	@Autowired
	MgrService mgr;
	@Autowired
	ContributionService contributions;
	@Autowired
	LedgerService ledger;
	@Autowired
	MemberRepository members;

	private Member member(String name, YearMonth mgrStart) {
		return members.save(new Member(name, JAN.atDay(1), mgrStart.atDay(1)));
	}

	private void pay(Member m, YearMonth month, long amount) {
		ContributionForm form = new ContributionForm();
		form.setMemberId(m.getId());
		form.setMonth(month);
		form.setAmount(Money.of(amount));
		form.setPaidDate(month.atDay(5));
		form.setMethod("M-Pesa");
		contributions.record(form);
	}

	private MgrService.RoundView round() {
		return mgr.currentRound().orElseThrow();
	}

	private MgrService.SlotView slotOf(Member m) {
		return round().slotOf(m.getId()).orElseThrow();
	}

	private static DecisionInput vote() {
		return DecisionInput.of(null, "WhatsApp vote, 4 of 5", "test");
	}

	@Test
	void aWholeRound() throws Exception {
		Member a = member("Mgr Alpha", JAN);
		Member b = member("Mgr Beta", JAN);
		Member c = member("Mgr Gamma", JAN);
		Member d = member("Mgr Delta", JAN);
		// Joins the MGR in April, but already has a place this round (like the new member in January 2027).
		Member e = member("Mgr Epsilon", APR);

		mgr.saveRound(null, 1, JAN, List.of(a.getId(), b.getId(), c.getId(), d.getId(), e.getId()));
		assertThat(round().slots()).extracting(MgrService.SlotView::month).containsExactly(JAN, FEB, MAR, APR,
				YearMonth.of(2020, 5));

		// --- January: the recipient sends nothing; everyone else pays in full.
		assertThatThrownBy(() -> mgr.payOut(slotOf(a).slot().getId(), JAN.atDay(10), "M-Pesa", null, true, null))
				.isInstanceOf(MgrException.class).hasMessageContaining("Not everyone has paid")
				.hasMessageContaining("Mgr Beta");
		pay(b, JAN, 3500);
		pay(c, JAN, 3500);
		pay(d, JAN, 3500);
		MgrService.PayoutPreview jan = mgr.preview(slotOf(a).slot().getId());
		// Pool before January's new member joins the MGR: 4 x 1,500. A's own part is netted.
		assertThat(jan.gross()).isEqualByComparingTo("6000");
		assertThat(jan.netIfDeducting()).isEqualByComparingTo("2500");

		MgrPayout janPayout = mgr.payOut(slotOf(a).slot().getId(), JAN.atDay(10), "M-Pesa", "QX1", true, null);

		assertThat(janPayout.getGross()).isEqualByComparingTo("6000");
		assertThat(janPayout.net()).isEqualByComparingTo("2500");
		assertThat(ledger.mgrBalanceFor(JAN)).isEqualByComparingTo("0");
		assertThat(ledger.sharesOf(a.getId())).isEqualByComparingTo("2000");
		assertThat(contributions.memberMonth(a.getId(), JAN).status()).isEqualTo(MonthStatus.PAID);
		assertThat(slotOf(a).status()).isEqualTo(MgrService.SlotStatus.PAID);

		// --- February: the recipient sends only their savings; their MGR part is netted.
		pay(a, FEB, 3500);
		pay(b, FEB, 2000);
		pay(c, FEB, 3500);
		pay(d, FEB, 3500);
		MgrPayout febPayout = mgr.payOut(slotOf(b).slot().getId(), FEB.atDay(9), "PayPal", null, true, null);
		assertThat(febPayout.getGross()).isEqualByComparingTo("6000");
		assertThat(febPayout.getNettedOwnMgr()).isEqualByComparingTo("1500");
		assertThat(febPayout.net()).isEqualByComparingTo("4500");

		// --- March: A has received, so can't opt out of the MGR unless the group decides (M5).
		assertThatThrownBy(() -> contributions.setOptOut(a.getId(), MAR, false, true, null, null))
				.isInstanceOf(ContributionException.class).hasMessageContaining("already received");
		contributions.setOptOut(a.getId(), MAR, false, true, "Emergency", vote());

		// D opts out of the MGR in March: loses this round's turn, and E moves up to April (Q2).
		contributions.setOptOut(d.getId(), MAR, false, true, null);
		assertThat(slotOf(d).status()).isEqualTo(MgrService.SlotStatus.LOST_TURN);
		assertThat(slotOf(e).month()).isEqualTo(APR);
		assertThat(round().activeSlots()).hasSize(4);

		// C receives in March; B hasn't paid MGR, so the payout needs a group decision (Q4).
		pay(a, MAR, 2000);
		pay(b, MAR, 2000);
		pay(c, MAR, 3500);
		pay(d, MAR, 2000);
		assertThatThrownBy(() -> mgr.payOut(slotOf(c).slot().getId(), MAR.atDay(10), "M-Pesa", null, false, null))
				.isInstanceOf(MgrException.class).hasMessageContaining("Mgr Beta");
		MgrPayout marPayout = mgr.payOut(slotOf(c).slot().getId(), MAR.atDay(10), "M-Pesa", null, false, vote());
		assertThat(marPayout.getGross()).isEqualByComparingTo("1500");
		assertThat(marPayout.getDecisionId()).isNotNull();

		// B pays late: a top-up pays it on to C.
		pay(b, MAR, 1500);
		assertThat(ledger.mgrBalanceFor(MAR)).isEqualByComparingTo("1500");
		MgrPayout topUp = mgr.payOut(slotOf(c).slot().getId(), MAR.atDay(20), "M-Pesa", null, false, null);
		assertThat(topUp.getGross()).isEqualByComparingTo("1500");
		assertThat(ledger.mgrBalanceFor(MAR)).isEqualByComparingTo("0");

		// Only the latest payout can be corrected; reversing it puts the money back.
		assertThatThrownBy(() -> mgr.voidPayout(marPayout.getId(), "typo")).isInstanceOf(MgrException.class);
		mgr.voidPayout(topUp.getId(), "typo");
		assertThat(ledger.mgrBalanceFor(MAR)).isEqualByComparingTo("1500");
		topUp = mgr.payOut(slotOf(c).slot().getId(), MAR.atDay(20), "M-Pesa", null, false, null);

		// --- April: the new member is in the MGR now, so the pool includes them.
		for (Member m : List.of(a, b, c, d, e)) {
			pay(m, APR, 3500);
		}
		MgrPayout aprPayout = mgr.payOut(slotOf(e).slot().getId(), APR.atDay(10), "M-Pesa", null, true, null);
		assertThat(aprPayout.getGross()).isEqualByComparingTo("7500");
		assertThat(round().isComplete()).isTrue();

		// --- Round 2: everyone back in, same order; D (who lost their turn) is included again.
		assertThatThrownBy(() -> mgr.saveRound(null, 3, YearMonth.of(2020, 5), List.of(a.getId())))
				.isInstanceOf(MgrException.class).hasMessageContaining("Round 2");
		mgr.saveRound(null, 2, YearMonth.of(2020, 5),
				List.of(a.getId(), b.getId(), c.getId(), d.getId(), e.getId()));
		assertThat(round().round().getNumber()).isEqualTo(2);
		assertThat(slotOf(d).status()).isNotEqualTo(MgrService.SlotStatus.LOST_TURN);

		// History is only for payouts before the app.
		assertThatThrownBy(() -> mgr.recordHistory(slotOf(a).slot().getId(), Money.of(9000), LocalDate.of(2020, 5, 10)))
				.isInstanceOf(MgrException.class).hasMessageContaining("history");

		// Pages.
		mvc.perform(get("/mgr")).andExpect(status().isOk()).andExpect(content().string(Matchers.containsString("Round 2")));
		mvc.perform(get("/mgr/rounds/new")).andExpect(status().isOk());
		// Round 2's first place (May 2020) is due, with nobody paid in yet.
		mvc.perform(get("/mgr/slots/{id}/payout", slotOf(a).slot().getId())).andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("Not paid")));
		mvc.perform(get("/members/{id}", a.getId())).andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("MGR payouts received")));
		mvc.perform(get("/")).andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("Merry-go-round rotation")));
	}
}

package com.personal.chama.pool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.personal.chama.IntegrationTest;
import com.personal.chama.common.Money;
import com.personal.chama.ledger.LedgerEntryType;
import com.personal.chama.ledger.LedgerService;
import com.personal.chama.member.Member;
import com.personal.chama.member.MemberRepository;

@IntegrationTest
@WithMockUser(username = "recorder-c", roles = "RECORDER")
class PoolIntegrationTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	FundEarningService fundEarnings;
	@Autowired
	FundEarningRepository earningRepository;
	@Autowired
	OpeningBalanceService openingBalances;
	@Autowired
	LedgerService ledger;
	@Autowired
	MemberRepository members;

	private Member newMember() {
		return members.save(new Member("Pool " + UUID.randomUUID().toString().substring(0, 8),
				LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 1)));
	}

	@Test
	void fundEarningsGrowThePoolButNobodysShares() {
		Member someone = newMember();
		BigDecimal poolBefore = ledger.mmfBalance();
		BigDecimal sharesBefore = ledger.sharesOf(someone.getId());

		fundEarnings.record(YearMonth.of(2026, 9), Money.of(1850), "Sep statement", null);

		assertThat(ledger.mmfBalance().subtract(poolBefore)).isEqualByComparingTo("1850");
		assertThat(ledger.sharesOf(someone.getId())).isEqualByComparingTo(sharesBefore);
	}

	@Test
	void correctingFundEarningsReversesThem() {
		BigDecimal poolBefore = ledger.mmfBalance();
		FundEarning saved = fundEarnings.record(YearMonth.of(2026, 9), Money.of(1580), null, null);

		fundEarnings.voidEarning(saved.getId(), "Typo");

		assertThat(ledger.mmfBalance()).isEqualByComparingTo(poolBefore);
		assertThatThrownBy(() -> fundEarnings.voidEarning(saved.getId(), "again")).isInstanceOf(PoolException.class);
	}

	@Test
	void earningsForAFutureMonthAreRefused() {
		assertThatThrownBy(() -> fundEarnings.record(YearMonth.now().plusMonths(2), Money.of(100), null, null))
				.isInstanceOf(PoolException.class);
	}

	@Test
	void statementCanBeUploadedAndViewed() throws Exception {
		String reference = "upload-" + UUID.randomUUID().toString().substring(0, 8);
		MockMultipartFile pdf = new MockMultipartFile("statement", "statement.pdf", "application/pdf",
				"%PDF-1.4 test".getBytes());

		mvc.perform(multipart("/fund-earnings").file(pdf).with(csrf())
				.param("month", "2026-09").param("amount", "1850").param("reference", reference))
				.andExpect(redirectedUrl("/fund-earnings"));

		FundEarning saved = earningRepository.findAll().stream()
				.filter(e -> reference.equals(e.getStatementReference())).findFirst().orElseThrow();
		assertThat(saved.hasStatement()).isTrue();

		mvc.perform(get("/fund-earnings/{id}/statement", saved.getId()))
				.andExpect(status().isOk())
				.andExpect(content().contentType("application/pdf"))
				.andExpect(content().bytes("%PDF-1.4 test".getBytes()));
	}

	@Test
	void statementMustBePdfOrImage() throws Exception {
		MockMultipartFile exe = new MockMultipartFile("statement", "evil.exe", "application/octet-stream", new byte[] { 1 });

		mvc.perform(multipart("/fund-earnings").file(exe).with(csrf())
				.param("month", "2026-09").param("amount", "100"))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("must be a PDF, PNG or JPEG")));
	}

	/**
	 * Opening balances are global, so this one test covers their whole life cycle and
	 * leaves them reversed afterwards.
	 */
	@Test
	void openingBalances() throws Exception {
		if (openingBalances.isRecorded()) {
			openingBalances.redo("test setup");
		}
		Member a = newMember();
		Member b = newMember();
		BigDecimal poolBefore = ledger.mmfBalance();

		// Loans out on the opening date (other tests may have entered some) count as group money.
		BigDecimal loansOut = openingBalances.loansOutAtOpening();

		// Shares can't exceed the statement balance plus loans out.
		Map<Long, BigDecimal> tooMuch = new LinkedHashMap<>();
		tooMuch.put(a.getId(), Money.of(50_001).add(loansOut));
		assertThatThrownBy(() -> openingBalances.record(tooMuch, Money.of(50_000)))
				.isInstanceOf(PoolException.class);

		Map<Long, BigDecimal> shares = new LinkedHashMap<>();
		shares.put(a.getId(), Money.of(34_000));
		shares.put(b.getId(), Money.of(30_000));
		openingBalances.record(shares, Money.of(70_000));

		assertThat(ledger.sharesOf(a.getId())).isEqualByComparingTo("34000");
		assertThat(ledger.sharesOf(b.getId())).isEqualByComparingTo("30000");
		// Opening entries add shares + group money = statement balance + loans out. The loans'
		// own disbursement entries already took that money out of cash, so cash = statement.
		assertThat(ledger.mmfBalance().subtract(poolBefore)).isEqualByComparingTo(Money.of(70_000).add(loansOut));
		assertThat(openingBalances.current()).extracting(e -> e.getType())
				.contains(LedgerEntryType.OPENING_GROUP_FUNDS);

		assertThatThrownBy(() -> openingBalances.record(shares, Money.of(70_000)))
				.isInstanceOf(PoolException.class)
				.hasMessageContaining("already recorded");

		mvc.perform(get("/opening-balances")).andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString(a.getFullName())));

		openingBalances.redo("test cleanup");
		assertThat(openingBalances.isRecorded()).isFalse();
		assertThat(ledger.mmfBalance()).isEqualByComparingTo(poolBefore);
	}

	@Test
	void pagesRender() throws Exception {
		mvc.perform(get("/fund-earnings")).andExpect(status().isOk());
		mvc.perform(get("/opening-balances")).andExpect(status().isOk());
	}
}

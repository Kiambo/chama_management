package com.personal.chama.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.personal.chama.IntegrationTest;
import com.personal.chama.common.Money;
import com.personal.chama.contribution.Contribution;
import com.personal.chama.contribution.ContributionException;
import com.personal.chama.contribution.ContributionForm;
import com.personal.chama.contribution.ContributionService;
import com.personal.chama.ledger.LedgerService;
import com.personal.chama.loan.Loan;
import com.personal.chama.loan.LoanException;
import com.personal.chama.loan.LoanForm;
import com.personal.chama.loan.LoanKind;
import com.personal.chama.loan.LoanService;
import com.personal.chama.loan.Tenure;
import com.personal.chama.member.Member;
import com.personal.chama.member.MemberRepository;

@IntegrationTest
@WithMockUser(username = "recorder-g", roles = "RECORDER")
class ReferenceIntegrationTest {

	private static final YearMonth OCT = YearMonth.of(2026, 10);

	@Autowired
	MockMvc mvc;
	@Autowired
	ContributionService contributions;
	@Autowired
	LoanService loans;
	@Autowired
	LedgerService ledger;
	@Autowired
	MemberRepository members;

	private static String uniqueRef() {
		return "RF" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
	}

	private Member member() {
		return members.save(new Member("Ref " + UUID.randomUUID().toString().substring(0, 8), LocalDate.of(2025, 3, 1),
				LocalDate.of(2025, 3, 1)));
	}

	private Contribution contribute(Member m, long amount, String reference) {
		ContributionForm form = new ContributionForm();
		form.setMemberId(m.getId());
		form.setMonth(OCT);
		form.setAmount(Money.of(amount));
		form.setPaidDate(LocalDate.of(2026, 10, 5));
		form.setMethod("M-Pesa");
		form.setReference(reference);
		return contributions.record(form);
	}

	private Loan loan(Member borrower) {
		LoanForm form = new LoanForm();
		form.setBorrowerId(borrower.getId());
		form.setAmount(BigDecimal.valueOf(5_000));
		form.setTenure(Tenure.ONE_MONTH);
		form.setDisbursedDate(LocalDate.now());
		form.setMethod("M-Pesa");
		form.setKind(LoanKind.EXISTING_AS_AGREED);
		form.setNote("test");
		return loans.disburse(form);
	}

	@Test
	void theSameReferenceCantBeUsedTwiceForTheSamePaymentKind() {
		Member m = member();
		String ref = uniqueRef();
		contribute(m, 2000, ref);
		assertThatThrownBy(() -> contribute(m, 1500, ref))
				.isInstanceOf(ContributionException.class)
				.hasMessageContaining("already used").hasMessageContaining("Contribution from " + m.getFullName());
	}

	@Test
	void norForADifferentKindOfPayment_ignoringCaseAndSpaces() {
		Member m = member();
		String ref = uniqueRef();
		contribute(m, 3500, ref);
		Loan loan = loan(m);
		String sameButMessy = " " + ref.substring(0, 4).toLowerCase() + " " + ref.substring(4).toLowerCase();
		assertThatThrownBy(() -> loans.repay(loan.getId(), Money.of(1000), LocalDate.now(), "M-Pesa", sameButMessy))
				.isInstanceOf(LoanException.class).hasMessageContaining("already used");
		// Nothing was saved for the refused repayment.
		assertThat(loans.view(loan.getId()).repayments()).isEmpty();
	}

	@Test
	void aCorrectedPaymentGivesItsReferenceBack() {
		Member m = member();
		String ref = uniqueRef();
		Contribution wrong = contribute(m, 2000, ref);
		contributions.voidContribution(wrong.getId(), "Wrong amount");
		Contribution right = contribute(m, 3500, ref);
		assertThat(right.getReference()).isEqualTo(ref);
	}

	@Test
	void blankReferencesArentTracked() {
		Member m = member();
		contribute(m, 2000, null);
		contribute(m, 1500, "  ");
		assertThat(contributions.memberMonth(m.getId(), OCT).paid().total()).isEqualByComparingTo("3500");
	}

	@Test
	void theFormSaysWhereTheReferenceWasUsed() throws Exception {
		Member m = member();
		String ref = uniqueRef();
		contribute(m, 2000, ref);
		mvc.perform(post("/contributions").with(csrf())
				.param("memberId", m.getId().toString()).param("month", "2026-10").param("amount", "1500")
				.param("paidDate", "2026-10-06").param("method", "M-Pesa").param("reference", ref.toLowerCase()))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("was already used")));
	}
}

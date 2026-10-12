package com.personal.chama.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.personal.chama.IntegrationTest;
import com.personal.chama.common.Money;
import com.personal.chama.ledger.LedgerEntry;
import com.personal.chama.ledger.LedgerEntryType;
import com.personal.chama.ledger.LedgerService;
import com.personal.chama.member.Member;
import com.personal.chama.member.MemberRepository;

/**
 * Tests share one database with other classes, so pool figures are checked as changes.
 * Test members join next month, so they have no contribution arrears today; their shares
 * are given to them directly as ledger entries.
 */
@IntegrationTest
@WithMockUser(username = "recorder-d", roles = "TREASURER")
class LoanIntegrationTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	LoanService loans;
	@Autowired
	LedgerService ledger;
	@Autowired
	MemberRepository members;

	private static boolean cushionAdded;

	/**
	 * Group money so the pool caps comfortably exceed the loans in these tests, whatever
	 * other test classes have lent before. Share limits then decide, as the tests expect.
	 */
	@BeforeEach
	void addCushion() {
		if (!cushionAdded) {
			ledger.post(LedgerEntry.of(LedgerEntryType.FUND_EARNINGS, Money.of(2_000_000), null, null, LocalDate.now(),
					"test cushion"));
			cushionAdded = true;
		}
	}

	private Member memberWithShares(long shares) {
		LocalDate nextMonth = YearMonth.now().plusMonths(1).atDay(1);
		Member m = members.save(new Member("Loan " + UUID.randomUUID().toString().substring(0, 8), nextMonth, nextMonth));
		if (shares > 0) {
			ledger.post(LedgerEntry.of(LedgerEntryType.CONTRIBUTION_MMF, Money.of(shares), m, null, LocalDate.now(), "test shares"));
		}
		return m;
	}

	private LoanForm form(Member borrower, long amount, Tenure tenure, Member... guarantors) {
		LoanForm form = new LoanForm();
		form.setBorrowerId(borrower.getId());
		form.setAmount(BigDecimal.valueOf(amount));
		form.setTenure(tenure);
		form.setDisbursedDate(LocalDate.now());
		form.setMethod("M-Pesa");
		form.setGuarantorIds(List.of(guarantors).stream().map(Member::getId).toList());
		return form;
	}

	@Test
	void disbursingMovesCashOutOnLoanWithoutChangingThePoolTotal() {
		Member borrower = memberWithShares(10_000);
		BigDecimal cashBefore = ledger.mmfBalance();
		BigDecimal totalBefore = ledger.poolTotal();
		BigDecimal outBefore = ledger.principalOutstanding();

		Loan loan = loans.disburse(form(borrower, 15_000, Tenure.ONE_MONTH));

		assertThat(cashBefore.subtract(ledger.mmfBalance())).isEqualByComparingTo("15000");
		assertThat(ledger.poolTotal()).isEqualByComparingTo(totalBefore);
		assertThat(ledger.principalOutstanding().subtract(outBefore)).isEqualByComparingTo("15000");
		assertThat(loan.getRatePercent()).isEqualByComparingTo("3");
		assertThat(loan.getInterest()).isEqualByComparingTo("450");
		assertThat(loans.view(loan.getId()).schedule().lines()).singleElement()
				.satisfies(l -> assertThat(l.installment().getAmount()).isEqualByComparingTo("15450"));
	}

	@Test
	void arrearsBlockBorrowing() {
		// Joined long ago and never paid: contributions are past due.
		Member owes = members.save(new Member("Owes " + UUID.randomUUID().toString().substring(0, 8),
				LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 1)));
		ledger.post(LedgerEntry.of(LedgerEntryType.CONTRIBUTION_MMF, Money.of(10_000), owes, null, LocalDate.now(), "test"));
		assertThat(loans.assess(owes.getId()).eligible()).isFalse();
		assertThatThrownBy(() -> loans.disburse(form(owes, 5_000, Tenure.ONE_MONTH)))
				.isInstanceOf(LoanException.class).hasMessageContaining("past due");
	}

	@Test
	void oneLoanAtATime() {
		Member borrower = memberWithShares(10_000);
		loans.disburse(form(borrower, 5_000, Tenure.ONE_MONTH));
		assertThatThrownBy(() -> loans.disburse(form(borrower, 5_000, Tenure.ONE_MONTH)))
				.isInstanceOf(LoanException.class).hasMessageContaining("already has an active loan");
	}

	@Test
	void cannotBorrowAboveTheLimit() {
		Member borrower = memberWithShares(5_000);
		assertThat(loans.assess(borrower.getId()).limit().limit()).isEqualByComparingTo("15000");
		assertThatThrownBy(() -> loans.disburse(form(borrower, 15_001, Tenure.ONE_MONTH)))
				.isInstanceOf(LoanException.class).hasMessageContaining("borrowing limit");
	}

	@Test
	void guarantorCountFollowsTheAmount() {
		Member borrower = memberWithShares(20_000);
		Member g1 = memberWithShares(30_000);
		Member g2 = memberWithShares(30_000);

		assertThatThrownBy(() -> loans.disburse(form(borrower, 25_000, Tenure.THREE_MONTHS)))
				.isInstanceOf(LoanException.class).hasMessageContaining("needs 1 guarantor");
		assertThatThrownBy(() -> loans.disburse(form(borrower, 15_000, Tenure.THREE_MONTHS, g1)))
				.isInstanceOf(LoanException.class).hasMessageContaining("doesn't need a guarantor");

		Loan loan = loans.disburse(form(borrower, 40_000, Tenure.SIX_MONTHS, g1, g2));
		assertThat(loans.view(loan.getId()).guarantors()).extracting(Member::getId)
				.containsExactlyInAnyOrder(g1.getId(), g2.getId());
	}

	@Test
	void guarantorNeedsEnoughShares() {
		Member borrower = memberWithShares(10_000);
		Member poor = memberWithShares(19_999);
		assertThatThrownBy(() -> loans.disburse(form(borrower, 25_000, Tenure.THREE_MONTHS, poor)))
				.isInstanceOf(LoanException.class).hasMessageContaining("at least 20,000");
	}

	@Test
	void guarantorWithArrearsIsRefused() {
		Member borrower = memberWithShares(10_000);
		Member owes = members.save(new Member("Owes " + UUID.randomUUID().toString().substring(0, 8),
				LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 1)));
		ledger.post(LedgerEntry.of(LedgerEntryType.CONTRIBUTION_MMF, Money.of(30_000), owes, null, LocalDate.now(), "test"));
		assertThatThrownBy(() -> loans.disburse(form(borrower, 25_000, Tenure.THREE_MONTHS, owes)))
				.isInstanceOf(LoanException.class).hasMessageContaining("past due");
	}

	@Test
	void guarantorBacksOneLoanAtATime() {
		Member g = memberWithShares(30_000);
		loans.disburse(form(memberWithShares(10_000), 25_000, Tenure.THREE_MONTHS, g));
		assertThatThrownBy(() -> loans.disburse(form(memberWithShares(10_000), 25_000, Tenure.THREE_MONTHS, g)))
				.isInstanceOf(LoanException.class).hasMessageContaining("already guarantees");
	}

	@Test
	void borrowerCannotGuaranteeThemselves() {
		Member borrower = memberWithShares(30_000);
		assertThatThrownBy(() -> loans.disburse(form(borrower, 25_000, Tenure.THREE_MONTHS, borrower)))
				.isInstanceOf(LoanException.class).hasMessageContaining("own loan");
	}

	@Test
	void noCircularGuarantees() {
		Member a = memberWithShares(30_000);
		Member b = memberWithShares(30_000);
		// A borrows with B as guarantor...
		loans.disburse(form(a, 25_000, Tenure.THREE_MONTHS, b));
		// ...so A can't guarantee a loan to B.
		assertThat(loans.guarantorOptions(b.getId())).filteredOn(o -> o.member().getId().equals(a.getId()))
				.singleElement().satisfies(o -> assertThat(o.problem()).contains("can't guarantee each other"));
	}

	@Test
	void repaymentsGoInterestFirstAndCloseTheLoan() {
		Member borrower = memberWithShares(10_000);
		Loan loan = loans.disburse(form(borrower, 15_000, Tenure.THREE_MONTHS));
		BigDecimal outBefore = ledger.principalOutstanding();

		loans.repay(loan.getId(), Money.of(5_250), LocalDate.now(), "M-Pesa", "R1");
		assertThat(ledger.interestPaidFor(loan.getId())).isEqualByComparingTo("250");
		assertThat(ledger.principalOutstandingFor(loan.getId())).isEqualByComparingTo("10000");
		assertThat(outBefore.subtract(ledger.principalOutstanding())).isEqualByComparingTo("5000");

		assertThatThrownBy(() -> loans.repay(loan.getId(), Money.of(10_501), LocalDate.now(), "M-Pesa", null))
				.isInstanceOf(LoanException.class).hasMessageContaining("more than the whole remaining balance");

		// Paying off early: the rest of the principal and all of the remaining interest.
		loans.repay(loan.getId(), Money.of(10_500), LocalDate.now(), "M-Pesa", "R2");
		LoanService.LoanView view = loans.view(loan.getId());
		assertThat(view.loan().getStatus()).isEqualTo(LoanStatus.REPAID);
		assertThat(view.interestPaid()).isEqualByComparingTo("750");
		assertThat(view.principalOutstanding()).isEqualByComparingTo("0");

		// A repaid member can borrow again.
		assertThat(loans.assess(borrower.getId()).activeLoan()).isNull();
	}

	@Test
	void onlyTheLatestRepaymentCanBeCorrected() {
		Member borrower = memberWithShares(10_000);
		Loan loan = loans.disburse(form(borrower, 15_000, Tenure.THREE_MONTHS));
		Repayment first = loans.repay(loan.getId(), Money.of(5_250), LocalDate.now(), "M-Pesa", null);
		Repayment second = loans.repay(loan.getId(), Money.of(10_500), LocalDate.now(), "M-Pesa", null);

		assertThatThrownBy(() -> loans.voidRepayment(loan.getId(), first.getId(), "typo"))
				.isInstanceOf(LoanException.class).hasMessageContaining("most recent");

		loans.voidRepayment(loan.getId(), second.getId(), "typo");
		LoanService.LoanView view = loans.view(loan.getId());
		assertThat(view.loan().getStatus()).isEqualTo(LoanStatus.ACTIVE);
		assertThat(view.principalOutstanding()).isEqualByComparingTo("10000");
		assertThat(view.remaining()).isEqualByComparingTo("10500");
	}

	@Test
	void existingLoanRecordedAsAgreedSkipsThePolicyChecks() {
		// Like the existing 50,000 loan: one guarantor, far above 3 x shares, one payment at the end.
		Member borrower = memberWithShares(0);
		Member guarantor = memberWithShares(30_000);
		LoanForm form = form(borrower, 50_000, Tenure.FOUR_MONTHS, guarantor);
		form.setKind(LoanKind.EXISTING_AS_AGREED);
		form.setSchedule(LoanForm.Schedule.SINGLE_AT_END);
		form.setDisbursedDate(LocalDate.of(2026, 8, 28));

		assertThatThrownBy(() -> loans.disburse(form)).isInstanceOf(LoanException.class).hasMessageContaining("Say why");

		form.setNote("Agreed before the app: one payment at the end");
		BigDecimal outAtOpeningBefore = ledger.principalOutstandingOn(LocalDate.of(2026, 9, 30));
		Loan loan = loans.disburse(form);

		LoanService.LoanView view = loans.view(loan.getId());
		assertThat(view.schedule().lines()).singleElement().satisfies(l -> {
			assertThat(l.installment().getDueDate()).isEqualTo(LocalDate.of(2026, 12, 28));
			assertThat(l.installment().getAmount()).isEqualByComparingTo("54000");
		});
		// Out on the opening date, so it feeds the opening balances.
		assertThat(ledger.principalOutstandingOn(LocalDate.of(2026, 9, 30)).subtract(outAtOpeningBefore))
				.isEqualByComparingTo("50000");
	}

	@Test
	void existingLoanCheckedAgainstThePolicyRunsTheRules() {
		Member borrower = memberWithShares(5_000);
		LoanForm form = form(borrower, 20_000, Tenure.ONE_MONTH);
		form.setKind(LoanKind.EXISTING_CHECKED);
		form.setDisbursedDate(LocalDate.now().minusDays(2));
		assertThatThrownBy(() -> loans.disburse(form)).isInstanceOf(LoanException.class).hasMessageContaining("borrowing limit");
	}

	@Test
	void cancellingALoanReversesTheDisbursement() {
		Member borrower = memberWithShares(10_000);
		BigDecimal cashBefore = ledger.mmfBalance();
		Loan loan = loans.disburse(form(borrower, 5_000, Tenure.TWO_WEEKS));

		loans.cancel(loan.getId(), "Entered twice");

		assertThat(ledger.mmfBalance()).isEqualByComparingTo(cashBefore);
		assertThat(loans.view(loan.getId()).loan().getStatus()).isEqualTo(LoanStatus.CANCELLED);
	}

	@Test
	void initialsAreRecordedOncePerParty() {
		Member borrower = memberWithShares(10_000);
		Member g = memberWithShares(30_000);
		Loan loan = loans.disburse(form(borrower, 25_000, Tenure.THREE_MONTHS, g));

		loans.recordInitials(loan.getId(), LoanSignature.Role.BORROWER, null, null, "ab", LocalDate.now());
		loans.recordInitials(loan.getId(), LoanSignature.Role.GUARANTOR, g.getId(), null, "CD", LocalDate.now());
		loans.recordInitials(loan.getId(), LoanSignature.Role.TREASURER, null, "Treasurer Name", "TN", LocalDate.now());

		LoanService.LoanView view = loans.view(loan.getId());
		assertThat(view.signature("BORROWER", null).getInitials()).isEqualTo("AB");
		assertThat(view.signature("GUARANTOR", g.getId()).getRecordedBy()).isEqualTo("recorder-d");

		assertThatThrownBy(() -> loans.recordInitials(loan.getId(), LoanSignature.Role.BORROWER, null, null, "AB", LocalDate.now()))
				.isInstanceOf(LoanException.class).hasMessageContaining("already initialled");
		Member stranger = memberWithShares(0);
		assertThatThrownBy(() -> loans.recordInitials(loan.getId(), LoanSignature.Role.GUARANTOR, stranger.getId(), null, "XY", LocalDate.now()))
				.isInstanceOf(LoanException.class).hasMessageContaining("not a guarantor");
	}

	@Test
	void repaymentWithAnOtherMethodIsKept() throws Exception {
		Member borrower = memberWithShares(10_000);
		Loan loan = loans.disburse(form(borrower, 5_000, Tenure.ONE_MONTH));
		mvc.perform(post("/loans/{id}/repayments", loan.getId()).with(csrf())
				.param("amount", "1000").param("paidDate", LocalDate.now().toString()).param("method", "Equity Bank cheque"))
				.andExpect(redirectedUrlPattern("/loans/*"));
		assertThat(loans.view(loan.getId()).repayments()).singleElement()
				.satisfies(r -> assertThat(r.getMethod()).isEqualTo("Equity Bank cheque"));
	}

	@Test
	void repaymentWithoutAMethodIsRefused() throws Exception {
		Member borrower = memberWithShares(10_000);
		Loan loan = loans.disburse(form(borrower, 5_000, Tenure.ONE_MONTH));
		mvc.perform(post("/loans/{id}/repayments", loan.getId()).with(csrf())
				.param("amount", "1000").param("paidDate", LocalDate.now().toString()).param("method", ""))
				.andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("Enter how it was paid")));
	}

	@Test
	void pagesRender() throws Exception {
		Member borrower = memberWithShares(10_000);
		Member g = memberWithShares(30_000);

		mvc.perform(get("/loans/new").param("borrowerId", borrower.getId().toString())).andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("Borrowing limit")));
		mvc.perform(post("/loans").with(csrf())
				.param("borrowerId", borrower.getId().toString()).param("amount", "25000").param("tenure", "THREE_MONTHS")
				.param("guarantorIds", g.getId().toString()).param("disbursedDate", LocalDate.now().toString())
				.param("method", "M-Pesa").param("kind", "NEW").param("schedule", "STANDARD").param("action", "review"))
				.andExpect(view().name("loans/confirm"))
				.andExpect(content().string(Matchers.containsString("26,250")));
		mvc.perform(post("/loans").with(csrf())
				.param("borrowerId", borrower.getId().toString()).param("amount", "25000").param("tenure", "THREE_MONTHS")
				.param("guarantorIds", g.getId().toString()).param("disbursedDate", LocalDate.now().toString())
				.param("method", "M-Pesa").param("kind", "NEW").param("schedule", "STANDARD").param("action", "disburse"))
				.andExpect(redirectedUrlPattern("/loans/*"));

		Loan loan = loans.forBorrower(borrower.getId()).get(0).loan();
		mvc.perform(get("/loans")).andExpect(status().isOk());
		// Breadcrumbs show the way back, and the payment method is a real dropdown with no preset value.
		mvc.perform(get("/loans/{id}", loan.getId())).andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("class=\"crumbs\"")))
				.andExpect(content().string(Matchers.containsString("<select name=\"method\"")))
				.andExpect(content().string(Matchers.not(Matchers.containsString("value=\"M-Pesa\" selected"))));
		mvc.perform(get("/loans/{id}/agreement", loan.getId())).andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString(g.getFullName())));
		mvc.perform(get("/members/{id}", borrower.getId())).andExpect(status().isOk());
		mvc.perform(get("/members/{id}", g.getId())).andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("Guaranteeing")));
		mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(Matchers.containsString("Active loans")));
		mvc.perform(get("/ledger")).andExpect(status().isOk());
	}
}

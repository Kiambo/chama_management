package com.personal.chama.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.personal.chama.IntegrationTest;
import com.personal.chama.common.Money;
import com.personal.chama.decision.DecisionException;
import com.personal.chama.decision.DecisionInput;
import com.personal.chama.decision.DecisionService;
import com.personal.chama.decision.GroupDecision;
import com.personal.chama.ledger.LedgerEntry;
import com.personal.chama.ledger.LedgerEntryType;
import com.personal.chama.ledger.LedgerService;
import com.personal.chama.member.Member;
import com.personal.chama.member.MemberRepository;

/**
 * Late fees, defaults, recovery and group decisions (Phase 4). Loans are entered
 * "recorded as agreed" with past disbursement dates, so payments are already missed
 * today. Grace is 5 days; the late fee is the higher of 500 or 5% of the installment.
 */
@IntegrationTest
@WithMockUser(username = "recorder-e", roles = "RECORDER")
class LatePaymentIntegrationTest {

	@Autowired
	MockMvc mvc;
	@Autowired
	LoanService loans;
	@Autowired
	LatePaymentService latePayments;
	@Autowired
	RecoveryService recovery;
	@Autowired
	LoanDecisionService loanDecisions;
	@Autowired
	DecisionService decisions;
	@Autowired
	LoanHistory history;
	@Autowired
	LedgerService ledger;
	@Autowired
	MemberRepository members;

	private LocalDate today() {
		return history.today();
	}

	/** Joins next month, so no contribution arrears today; shares given directly. */
	private Member member(long shares) {
		LocalDate nextMonth = YearMonth.now().plusMonths(1).atDay(1);
		Member m = members.save(new Member("Late " + UUID.randomUUID().toString().substring(0, 8), nextMonth, nextMonth));
		if (shares > 0) {
			ledger.post(LedgerEntry.of(LedgerEntryType.CONTRIBUTION_MMF, Money.of(shares), m, null, today(), "test shares"));
		}
		return m;
	}

	private Loan loan(Member borrower, long amount, Tenure tenure, LocalDate disbursed, Member... guarantors) {
		LoanForm form = new LoanForm();
		form.setBorrowerId(borrower.getId());
		form.setAmount(BigDecimal.valueOf(amount));
		form.setTenure(tenure);
		form.setDisbursedDate(disbursed);
		form.setMethod("M-Pesa");
		form.setKind(LoanKind.EXISTING_AS_AGREED);
		form.setNote("test");
		form.setGuarantorIds(List.of(guarantors).stream().map(Member::getId).toList());
		Loan loan = loans.disburse(form);
		latePayments.reconcile(loan.getId());
		return loan;
	}

	private LoanService.LoanView view(Loan loan) {
		return loans.view(loan.getId());
	}

	private static DecisionInput vote() {
		return DecisionInput.of(null, "WhatsApp vote, 6 of 7", "test");
	}

	@Test
	void feeIsTheHigherOf500Or5Percent() {
		Loan loan = loan(member(0), 15_000, Tenure.THREE_MONTHS, today());
		List<RepaymentSchedule.Line> lines = view(loan).schedule().activeLines();
		// 5,250 x 5% = 262.50, so the 500 minimum applies.
		assertThat(latePayments.feeFor(lines.get(0).installment())).isEqualByComparingTo("500");
		Loan big = loan(member(0), 50_000, Tenure.FOUR_MONTHS, today());
		// 13,500 x 5% = 675.
		assertThat(latePayments.feeFor(view(big).schedule().activeLines().get(0).installment())).isEqualByComparingTo("675");
	}

	@Test
	void missedPaymentGetsOneFeeAfterTheGraceDays() {
		Loan loan = loan(member(0), 5_000, Tenure.ONE_MONTH, today().minusMonths(1).minusDays(6));
		latePayments.reconcile(loan.getId());
		latePayments.reconcile(loan.getId());

		LoanService.LoanView v = view(loan);
		assertThat(v.fees()).singleElement().satisfies(f -> assertThat(f.getAmount()).isEqualByComparingTo("500"));
		assertThat(v.flag()).isEqualTo(LoanFlag.OVERDUE);
		assertThat(v.remaining()).isEqualByComparingTo("5650");
		assertThat(loans.overdueAmount(loan.getBorrower().getId())).isEqualByComparingTo("5650");
		// A single-payment loan: recovery waits until it is a month overdue (R4).
		assertThat(v.recoveryAvailable()).isFalse();
	}

	@Test
	void withinTheGraceDaysThereIsNoFeeAndNoArrears() {
		Loan loan = loan(member(0), 5_000, Tenure.ONE_MONTH, today().minusMonths(1).minusDays(3));
		LoanService.LoanView v = view(loan);
		assertThat(v.fees()).isEmpty();
		assertThat(v.flag()).isEqualTo(LoanFlag.DUE_SOON);
		assertThat(v.statusOf(v.schedule().activeLines().get(0))).isEqualTo("In grace");
		assertThat(loans.overdueAmount(loan.getBorrower().getId())).isEqualByComparingTo("0");
	}

	@Test
	void twoMissesInARowMakeTheWholeBalanceDue() {
		Loan loan = loan(member(0), 15_000, Tenure.THREE_MONTHS, today().minusMonths(2).minusDays(10));
		LoanService.LoanView v = view(loan);
		assertThat(v.loan().getStatus()).isEqualTo(LoanStatus.DEFAULTED);
		assertThat(v.fees()).hasSize(2);
		// Whole balance: all three installments (15,750) plus two fees.
		assertThat(v.dueNow()).isEqualByComparingTo("16750");
		assertThat(v.recoveryAvailable()).isTrue();
	}

	@Test
	void aMissThenAnOnTimePaymentIsNotTwoInARow() {
		Loan loan = loan(member(0), 15_000, Tenure.THREE_MONTHS, today().minusMonths(3).minusDays(10));
		List<RepaymentSchedule.Line> lines = view(loan).schedule().activeLines();
		LocalDate secondDue = lines.get(1).installment().getDueDate();
		// Recorded late, but paid on the 2nd due date: covers installment 1 with its fee, and installment 2.
		loans.repay(loan.getId(), Money.of(11_000), secondDue, "M-Pesa", null);

		LoanService.LoanView v = view(loan);
		assertThat(v.loan().getStatus()).isEqualTo(LoanStatus.ACTIVE);
		// Fees on installments 1 and 3; the one briefly charged on 2 was cancelled.
		assertThat(v.fees()).extracting(f -> v.installmentOf(f).getNumber()).containsExactlyInAnyOrder(1, 3);
		assertThat(v.flag()).isEqualTo(LoanFlag.OVERDUE);
		// "Paid on" is the date of the payment that cleared each installment, not its due date.
		List<RepaymentSchedule.Line> after = v.schedule().activeLines();
		assertThat(after.get(0).paidOn()).isEqualTo(secondDue);
		assertThat(after.get(1).paidOn()).isEqualTo(secondDue);
		assertThat(after.get(2).paidOn()).isNull();
	}

	@Test
	void recoveryFromTheBorrowersOwnShares() {
		Member borrower = member(10_000);
		Loan loan = loan(borrower, 5_000, Tenure.ONE_MONTH, today().minusMonths(2).minusDays(10));
		BigDecimal cashBefore = ledger.mmfBalance();
		assertThat(view(loan).recoveryAvailable()).isTrue();

		RecoveryService.Plan plan = recovery.recover(loan.getId());

		assertThat(plan.recovered()).isEqualByComparingTo("5650");
		assertThat(plan.poolLoss()).isEqualByComparingTo("0");
		assertThat(ledger.sharesOf(borrower.getId())).isEqualByComparingTo("4350");
		// No cash moves: the shares pay the loan.
		assertThat(ledger.mmfBalance()).isEqualByComparingTo(cashBefore);
		LoanService.LoanView v = view(loan);
		assertThat(v.loan().getStatus()).isEqualTo(LoanStatus.REPAID);
		assertThat(v.interestPaid()).isEqualByComparingTo("150");
		assertThat(v.debts()).isEmpty();

		// The borrower's page shows the whole story, oldest first.
		assertThat(loans.timeline(v)).extracting(LoanEvent::label)
				.containsExactly("Disbursed", "Late fee charged", "Recovered from own shares", "Repaid in full");
	}

	@Test
	void memberPageShowsTheirLoanHistory() throws Exception {
		Member borrower = member(10_000);
		Loan loan = loan(borrower, 5_000, Tenure.ONE_MONTH, today().minusMonths(2).minusDays(10));
		recovery.recover(loan.getId());

		mvc.perform(get("/members/{id}", borrower.getId())).andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("Loan history")))
				.andExpect(content().string(Matchers.containsString("Recovered from own shares")))
				.andExpect(content().string(Matchers.containsString("Late fee charged")))
				.andExpect(content().string(Matchers.containsString("Repaid in full")));
	}

	@Test
	void guarantorsShareTheShortfallAndAreOwedIt() {
		Member borrower = member(1_000);
		Member a = member(30_000);
		Member b = member(5_000);
		Loan loan = loan(borrower, 30_000, Tenure.THREE_MONTHS, today().minusMonths(2).minusDays(10), a, b);
		// Defaulted: 31,500 + two fees of 525 (5% of 10,500) = 32,550 due.
		assertThat(view(loan).dueNow()).isEqualByComparingTo("32550");

		RecoveryService.Plan plan = recovery.recover(loan.getId());

		// Borrower 1,000; the rest 31,550 split 15,775 each; B only has 5,000 so A covers the rest.
		assertThat(plan.poolLoss()).isEqualByComparingTo("0");
		assertThat(ledger.sharesOf(borrower.getId())).isEqualByComparingTo("0");
		assertThat(ledger.sharesOf(b.getId())).isEqualByComparingTo("0");
		assertThat(ledger.sharesOf(a.getId())).isEqualByComparingTo("3450");
		LoanService.LoanView v = view(loan);
		assertThat(v.loan().getStatus()).isEqualTo(LoanStatus.REPAID);
		assertThat(v.debts()).extracting(d -> d.debt().getGuarantor().getId(), d -> d.owed())
				.containsExactlyInAnyOrder(
						org.assertj.core.groups.Tuple.tuple(a.getId(), Money.of(26_550)),
						org.assertj.core.groups.Tuple.tuple(b.getId(), Money.of(5_000)));

		// The borrower repays guarantor A: A's shares come back by that much.
		LoanService.DebtView debtToA = v.debts().stream().filter(d -> d.debt().getGuarantor().getId().equals(a.getId()))
				.findFirst().orElseThrow();
		loanDecisions.repayGuarantor(debtToA.debt().getId(), Money.of(1_000), today(), "M-Pesa", null);
		assertThat(ledger.sharesOf(a.getId())).isEqualByComparingTo("4450");
		assertThat(loans.debtsInvolving(a.getId())).singleElement()
				.satisfies(d -> assertThat(d.owed()).isEqualByComparingTo("25550"));
		assertThatThrownBy(() -> loanDecisions.repayGuarantor(debtToA.debt().getId(), Money.of(30_000), today(), "M-Pesa", null))
				.isInstanceOf(LoanException.class).hasMessageContaining("more than is owed");
	}

	@Test
	void aShortfallBecomesAPoolLossThatCanBeWrittenOff() {
		Member borrower = member(2_000);
		Loan loan = loan(borrower, 10_000, Tenure.ONE_MONTH, today().minusMonths(2).minusDays(10));

		RecoveryService.Plan plan = recovery.recover(loan.getId());
		// Owed 10,300 + fee 515 = 10,815; 2,000 from shares; 8,815 can't be covered.
		assertThat(plan.poolLoss()).isEqualByComparingTo("8815");
		LoanService.LoanView v = view(loan);
		assertThat(v.loan().isAwaitingDecision()).isTrue();
		assertThat(v.loan().getStatus()).isEqualTo(LoanStatus.DEFAULTED);
		// The 2,000 paid the fee (515) and interest (300) first, then 1,185 principal.
		assertThat(v.principalOutstanding()).isEqualByComparingTo("8815");

		BigDecimal poolBefore = ledger.poolTotal();
		assertThatThrownBy(() -> loanDecisions.writeOff(loan.getId(), DecisionInput.of(null, " ", null)))
				.isInstanceOf(DecisionException.class);
		loanDecisions.writeOff(loan.getId(), vote());

		assertThat(view(loan).loan().getStatus()).isEqualTo(LoanStatus.WRITTEN_OFF);
		assertThat(ledger.principalOutstandingFor(loan.getId())).isEqualByComparingTo("0");
		assertThat(poolBefore.subtract(ledger.poolTotal())).isEqualByComparingTo("8815");
		assertThat(decisions.forLoan(loan.getId())).extracting(GroupDecision::getKind).contains(GroupDecision.Kind.WRITE_OFF);
	}

	@Test
	void aPoolLossCanBeRestructured() {
		Loan loan = loan(member(2_000), 10_000, Tenure.ONE_MONTH, today().minusMonths(2).minusDays(10));
		recovery.recover(loan.getId());

		loanDecisions.changeSchedule(loan.getId(), today().plusMonths(1), 2, Money.ZERO, true, vote());

		LoanService.LoanView v = view(loan);
		assertThat(v.loan().getStatus()).isEqualTo(LoanStatus.RESTRUCTURED);
		assertThat(v.loan().isAwaitingDecision()).isFalse();
		assertThat(v.schedule().activeLines()).hasSize(2);
		assertThat(v.remaining()).isEqualByComparingTo("8815");
		assertThat(v.schedule().lines()).anyMatch(l -> l.installment().isSuperseded());
		assertThat(v.flag()).isEqualTo(LoanFlag.ON_TRACK);
	}

	@Test
	void aFeeCanBeWaivedByGroupDecision() {
		Loan loan = loan(member(0), 5_000, Tenure.ONE_MONTH, today().minusMonths(1).minusDays(6));
		LateFee fee = view(loan).fees().get(0);

		assertThatThrownBy(() -> loanDecisions.waiveFee(loan.getId(), fee.getId(), DecisionInput.of(null, "", null)))
				.isInstanceOf(DecisionException.class).hasMessageContaining("how the group agreed");
		loanDecisions.waiveFee(loan.getId(), fee.getId(), vote());

		LoanService.LoanView v = view(loan);
		assertThat(v.remaining()).isEqualByComparingTo("5150");
		assertThat(v.fees()).singleElement().satisfies(f -> assertThat(f.isWaived()).isTrue());
		assertThat(v.decisions()).extracting(GroupDecision::getKind).containsExactly(GroupDecision.Kind.FEE_WAIVER);
		// Reconciling again doesn't bring the fee back.
		latePayments.reconcile(loan.getId());
		assertThat(view(loan).remaining()).isEqualByComparingTo("5150");
	}

	@Test
	void extendingMovesTheLoanToTheNewTier() {
		Loan loan = loan(member(0), 15_000, Tenure.THREE_MONTHS, today());
		assertThatThrownBy(() -> loanDecisions.extend(loan.getId(), Tenure.TWO_MONTHS, vote()))
				.isInstanceOf(LoanException.class).hasMessageContaining("longer");

		loanDecisions.extend(loan.getId(), Tenure.FIVE_MONTHS, vote());

		LoanService.LoanView v = view(loan);
		// 5% (750) becomes 8% (1,200): +450, spread with the rest over five months.
		assertThat(v.loan().getRatePercent()).isEqualByComparingTo("8");
		assertThat(v.loan().getInterest()).isEqualByComparingTo("1200");
		assertThat(v.remaining()).isEqualByComparingTo("16200");
		assertThat(v.schedule().activeLines()).hasSize(5);
		assertThat(v.schedule().lines().stream().filter(l -> l.installment().isSuperseded())).hasSize(3);
	}

	@Test
	void aRepaymentCanNotBeCorrectedAfterTheScheduleChanged() {
		Loan loan = loan(member(0), 15_000, Tenure.THREE_MONTHS, today());
		Repayment r = loans.repay(loan.getId(), Money.of(1_000), today(), "M-Pesa", null);
		loanDecisions.changeSchedule(loan.getId(), today().plusMonths(2), 2, null, false, vote());

		assertThatThrownBy(() -> loans.voidRepayment(loan.getId(), r.getId(), "typo"))
				.isInstanceOf(LoanException.class).hasMessageContaining("schedule was changed");
		assertThat(view(loan).remaining()).isEqualByComparingTo("14750");
	}

	@Test
	void pagesRender() throws Exception {
		Member borrower = member(1_000);
		Member g = member(30_000);
		Loan loan = loan(borrower, 25_000, Tenure.THREE_MONTHS, today().minusMonths(2).minusDays(10), g);
		Long feeId = view(loan).fees().get(0).getId();

		mvc.perform(get("/loans/{id}", loan.getId())).andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("Defaulted")))
				.andExpect(content().string(Matchers.containsString("Late fees")));
		mvc.perform(get("/loans/{id}/recover", loan.getId())).andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString(g.getFullName())));
		mvc.perform(get("/loans/{id}/fees/{fid}/waive", loan.getId(), feeId)).andExpect(status().isOk());
		mvc.perform(get("/loans/{id}/schedule", loan.getId()).param("restructure", "true")).andExpect(status().isOk());
		mvc.perform(get("/loans/{id}/write-off", loan.getId())).andExpect(status().isOk());

		recovery.recover(loan.getId());
		mvc.perform(get("/loans/{id}", loan.getId())).andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("Owed to guarantors")));
		mvc.perform(get("/members/{id}", g.getId())).andExpect(status().isOk())
				.andExpect(content().string(Matchers.containsString("Debts between members")));

		Loan fresh = loan(member(0), 15_000, Tenure.THREE_MONTHS, today());
		mvc.perform(get("/loans/{id}/extend", fresh.getId())).andExpect(status().isOk());
		mvc.perform(get("/loans/{id}/schedule", fresh.getId())).andExpect(status().isOk());
		mvc.perform(get("/decisions")).andExpect(status().isOk());
		mvc.perform(get("/")).andExpect(status().isOk());
	}
}

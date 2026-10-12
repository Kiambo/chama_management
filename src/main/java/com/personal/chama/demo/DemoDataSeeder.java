package com.personal.chama.demo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.personal.chama.common.Money;
import com.personal.chama.config.ChamaProperties;
import com.personal.chama.contribution.ContributionForm;
import com.personal.chama.contribution.ContributionService;
import com.personal.chama.loan.LoanForm;
import com.personal.chama.loan.LoanKind;
import com.personal.chama.loan.LoanService;
import com.personal.chama.loan.Tenure;
import com.personal.chama.member.Member;
import com.personal.chama.member.MemberRepository;
import com.personal.chama.mgr.MgrService;
import com.personal.chama.pool.FundEarningService;
import com.personal.chama.pool.OpeningBalanceService;

/**
 * Made-up data for trying the app, only with the "demo" profile and only into an empty
 * database (the separate chama_demo database). Names are obviously fictional.
 */
@Component
@Profile("demo")
@Order(10)
public class DemoDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

	private final MemberRepository members;
	private final ContributionService contributions;
	private final OpeningBalanceService openingBalances;
	private final FundEarningService fundEarnings;
	private final LoanService loans;
	private final MgrService mgr;
	private final ChamaProperties properties;
	private final Clock clock;

	public DemoDataSeeder(MemberRepository members, ContributionService contributions,
			OpeningBalanceService openingBalances, FundEarningService fundEarnings, LoanService loans, MgrService mgr,
			ChamaProperties properties, Clock clock) {
		this.members = members;
		this.contributions = contributions;
		this.openingBalances = openingBalances;
		this.fundEarnings = fundEarnings;
		this.loans = loans;
		this.mgr = mgr;
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (members.count() > 0) {
			return;
		}
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				"demo-seed", null, AuthorityUtils.createAuthorityList("ROLE_RECORDER")));
		try {
			seed();
		} finally {
			SecurityContextHolder.clearContext();
		}
		log.info("Demo data created");
	}

	private void seed() {
		LocalDate founded = LocalDate.of(2025, 3, 1);
		YearMonth cutover = properties.cutoverMonth();
		List<Member> founders = List.of(
				members.save(new Member("Amani Demo", founded, founded)),
				members.save(new Member("Baraka Sample", founded, founded)),
				members.save(new Member("Chiku Example", founded, founded)),
				members.save(new Member("Dalila Test", founded, founded)),
				members.save(new Member("Eli Placeholder", founded, founded)),
				members.save(new Member("Faraja Fictional", founded, founded)));
		// Joins after the founders: savings back-paid, MGR from a later month.
		Member newcomer = members.save(new Member("Gathoni Newcomer", cutover.minusMonths(1).atDay(1),
				cutover.plusMonths(3).atDay(1)));

		// A loan made before the app on agreed terms: one payment at the end. Entered before
		// the opening balances, because it was out on the opening date.
		LoanForm existing = loanForm(newcomer, 50_000, Tenure.FOUR_MONTHS, cutover.minusMonths(2).atDay(28),
				founders.get(4));
		existing.setKind(LoanKind.EXISTING_AS_AGREED);
		existing.setSchedule(LoanForm.Schedule.SINGLE_AT_END);
		existing.setNote("Agreed before the app: one payment at the end");
		loans.disburse(existing);

		Map<Long, BigDecimal> shares = new LinkedHashMap<>();
		long[] amounts = { 34_000, 30_000, 28_000, 34_000, 26_000, 32_000 };
		for (int i = 0; i < founders.size(); i++) {
			shares.put(founders.get(i).getId(), Money.of(amounts[i]));
		}
		openingBalances.record(shares, Money.of(146_500));
		fundEarnings.record(cutover.minusMonths(1), Money.of(1_850), "Demo statement", null);

		// This month: a mix of paid, late, part-paid and not yet paid.
		YearMonth month = YearMonth.now(clock);
		LocalDate today = LocalDate.now(clock);
		LocalDate onTime = month.atDay(Math.min(5, today.getDayOfMonth()));
		pay(founders.get(0), month, 3500, onTime, "M-Pesa", false);
		pay(founders.get(1), month, 3500, onTime, "PayPal", false);
		pay(founders.get(2), month, 3500, today, "Bank deposit", false);
		pay(founders.get(3), month, 2000, onTime, "M-Pesa", false);
		pay(newcomer, cutover.minusMonths(1), 2000, today, "M-Pesa", true);

		// A new loan, guaranteed, through the normal checks.
		LoanForm loan = loanForm(founders.get(0), 25_000, Tenure.THREE_MONTHS, today, founders.get(1));
		loan.setPurpose("School fees");
		loans.disburse(loan);

		// Late loans to try Phase 4 with: one defaulted (two missed in a row, with a
		// guarantor), one with a single missed payment and its late fee.
		LoanForm defaulted = loanForm(founders.get(2), 15_000, Tenure.THREE_MONTHS, today.minusMonths(2).minusDays(10),
				founders.get(5));
		defaulted.setKind(LoanKind.EXISTING_AS_AGREED);
		defaulted.setNote("Demo: two payments missed");
		loans.disburse(defaulted);
		LoanForm missed = loanForm(founders.get(3), 5_000, Tenure.ONE_MONTH, today.minusMonths(1).minusDays(8),
				founders.get(1));
		missed.setKind(LoanKind.EXISTING_AS_AGREED);
		missed.setNote("Demo: one payment missed");
		missed.setGuarantorIds(List.of());
		loans.disburse(missed);

		// The rotation: Round 5 from the month before the cut-over, the newcomer last. The
		// first month was paid before the app, so it's recorded as history.
		List<Long> order = new java.util.ArrayList<>(founders.stream().map(Member::getId).toList());
		order.add(newcomer.getId());
		mgr.saveRound(null, 5, cutover.minusMonths(1), order);
		mgr.recordHistory(mgr.currentRound().orElseThrow().slots().get(0).slot().getId(), Money.of(9_000),
				cutover.minusMonths(1).atDay(10));
	}

	private LoanForm loanForm(Member borrower, long amount, Tenure tenure, LocalDate date, Member guarantor) {
		LoanForm form = new LoanForm();
		form.setBorrowerId(borrower.getId());
		form.setAmount(BigDecimal.valueOf(amount));
		form.setTenure(tenure);
		form.setDisbursedDate(date);
		form.setMethod("M-Pesa");
		form.setGuarantorIds(List.of(guarantor.getId()));
		return form;
	}

	private void pay(Member member, YearMonth month, long amount, LocalDate paidOn, String method, boolean backpay) {
		ContributionForm form = new ContributionForm();
		form.setMemberId(member.getId());
		form.setMonth(month);
		form.setAmount(Money.of(amount));
		form.setPaidDate(paidOn);
		form.setMethod(method);
		form.setAgreedBackpay(backpay);
		contributions.record(form);
	}
}

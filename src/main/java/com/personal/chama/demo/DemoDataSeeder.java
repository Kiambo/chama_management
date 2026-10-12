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
import com.personal.chama.member.Member;
import com.personal.chama.member.MemberRepository;
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
	private final ChamaProperties properties;
	private final Clock clock;

	public DemoDataSeeder(MemberRepository members, ContributionService contributions,
			OpeningBalanceService openingBalances, FundEarningService fundEarnings, ChamaProperties properties,
			Clock clock) {
		this.members = members;
		this.contributions = contributions;
		this.openingBalances = openingBalances;
		this.fundEarnings = fundEarnings;
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

		Map<Long, BigDecimal> shares = new LinkedHashMap<>();
		long[] amounts = { 34_000, 30_000, 28_000, 34_000, 26_000, 32_000 };
		for (int i = 0; i < founders.size(); i++) {
			shares.put(founders.get(i).getId(), Money.of(amounts[i]));
		}
		openingBalances.record(shares, Money.of(196_500));
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

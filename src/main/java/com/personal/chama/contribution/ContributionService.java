package com.personal.chama.contribution;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.chama.config.ChamaProperties;
import com.personal.chama.ledger.LedgerEntry;
import com.personal.chama.ledger.LedgerEntryRepository;
import com.personal.chama.ledger.LedgerEntryType;
import com.personal.chama.ledger.LedgerService;
import com.personal.chama.member.Member;
import com.personal.chama.member.MemberNotFoundException;
import com.personal.chama.member.MemberRepository;

@Service
@Transactional(readOnly = true)
public class ContributionService {

	private final ContributionRepository contributions;
	private final ContributionOptOutRepository optOuts;
	private final MemberRepository members;
	private final LedgerService ledger;
	private final LedgerEntryRepository ledgerEntries;
	private final ContributionRules rules;
	private final AuditorAware<String> auditor;
	private final Clock clock;
	private final YearMonth cutover;

	public ContributionService(ContributionRepository contributions, ContributionOptOutRepository optOuts,
			MemberRepository members, LedgerService ledger, LedgerEntryRepository ledgerEntries,
			ContributionRules rules, AuditorAware<String> auditor, Clock clock, ChamaProperties properties) {
		this.contributions = contributions;
		this.optOuts = optOuts;
		this.members = members;
		this.ledger = ledger;
		this.ledgerEntries = ledgerEntries;
		this.rules = rules;
		this.auditor = auditor;
		this.clock = clock;
		this.cutover = properties.cutoverMonth();
	}

	public YearMonth currentMonth() {
		return YearMonth.now(clock);
	}

	public LocalDate today() {
		return LocalDate.now(clock);
	}

	public MemberMonth memberMonth(Long memberId, YearMonth month) {
		Member member = members.findById(memberId).orElseThrow(() -> new MemberNotFoundException(memberId));
		return build(member, month, optOuts.findByMemberIdAndMonth(memberId, month).orElse(null),
				contributions.findByMemberIdAndMonthAndVoidedAtIsNull(memberId, month));
	}

	/** Every active member who had joined by this month. */
	public List<MemberMonth> monthOverview(YearMonth month) {
		Map<Long, ContributionOptOut> monthOptOuts = optOuts.findByMonth(month).stream()
				.collect(Collectors.toMap(o -> o.getMember().getId(), Function.identity()));
		Map<Long, List<Contribution>> monthPayments = contributions.findByMonthAndVoidedAtIsNull(month).stream()
				.collect(Collectors.groupingBy(c -> c.getMember().getId()));
		return members.findByActiveTrueOrderByFullNameAsc().stream()
				.filter(m -> rules.isMemberIn(m, month))
				.map(m -> build(m, month, monthOptOuts.get(m.getId()), monthPayments.getOrDefault(m.getId(), List.of())))
				.toList();
	}

	/**
	 * A member's months, newest first: every month from the cut-over (or joining, if
	 * later) up to now, plus any earlier month with a recorded payment.
	 */
	public List<MemberMonth> history(Long memberId) {
		Member member = members.findById(memberId).orElseThrow(() -> new MemberNotFoundException(memberId));
		Map<YearMonth, List<Contribution>> byMonth = contributions.findByMemberIdAndVoidedAtIsNullOrderByPaidDateAsc(memberId)
				.stream().collect(Collectors.groupingBy(Contribution::getMonth));
		Map<YearMonth, ContributionOptOut> optOutByMonth = optOuts.findByMemberId(memberId).stream()
				.collect(Collectors.toMap(ContributionOptOut::getMonth, Function.identity()));

		YearMonth joined = YearMonth.from(member.getJoinDate());
		YearMonth from = joined.isAfter(cutover) ? joined : cutover;
		List<YearMonth> months = new ArrayList<>(byMonth.keySet());
		for (YearMonth m = from; !m.isAfter(currentMonth()); m = m.plusMonths(1)) {
			if (!months.contains(m)) {
				months.add(m);
			}
		}
		months.sort(Comparator.reverseOrder());
		return months.stream()
				.map(m -> build(member, m, optOutByMonth.get(m), byMonth.getOrDefault(m, List.of())))
				.toList();
	}

	@Transactional
	public Contribution record(ContributionForm form) {
		Member member = members.findByIdForUpdate(form.getMemberId())
				.orElseThrow(() -> new MemberNotFoundException(form.getMemberId()));
		YearMonth month = form.getMonth();
		if (!rules.isMemberIn(member, month)) {
			throw new ContributionException(member.getFullName() + " had not joined yet in that month.");
		}
		if (form.getPaidDate().isAfter(today())) {
			throw new ContributionException("The payment date can't be in the future.");
		}
		MemberMonth current = build(member, month, optOuts.findByMemberIdAndMonth(member.getId(), month).orElse(null),
				contributions.findByMemberIdAndMonthAndVoidedAtIsNull(member.getId(), month));
		Portions split = rules.split(form.getAmount(), current.due(), current.paid());

		Contribution contribution = contributions.save(new Contribution(member, month, split, form.getPaidDate(),
				form.getMethod().strip(), blankToNull(form.getReference()), form.isAgreedBackpay(),
				blankToNull(form.getNote())));
		if (split.mmf().signum() > 0) {
			ledger.post(LedgerEntry.forContribution(LedgerEntryType.CONTRIBUTION_MMF, split.mmf(), member, month,
					form.getPaidDate(), contribution.getId()));
		}
		if (split.mgr().signum() > 0) {
			ledger.post(LedgerEntry.forContribution(LedgerEntryType.CONTRIBUTION_MGR, split.mgr(), member, month,
					form.getPaidDate(), contribution.getId()));
		}
		return contribution;
	}

	/** Corrects a mistaken contribution: reverses its ledger entries and marks it voided. */
	@Transactional
	public void voidContribution(Long contributionId, String reason) {
		Contribution contribution = contributions.findById(contributionId)
				.orElseThrow(() -> new ContributionException("No such contribution."));
		contribution.voidWith(reason, auditor.getCurrentAuditor().orElse("system"), Instant.now(clock));
		for (LedgerEntry entry : ledgerEntries.findByContributionIdAndReversesIdIsNull(contributionId)) {
			if (!ledger.isReversed(entry.getId())) {
				ledger.reverse(entry.getId(), reason);
			}
		}
	}

	@Transactional
	public void setOptOut(Long memberId, YearMonth month, boolean savings, boolean mgr, String note) {
		Member member = members.findByIdForUpdate(memberId).orElseThrow(() -> new MemberNotFoundException(memberId));
		Portions paid = ContributionRules.paidBy(contributions.findByMemberIdAndMonthAndVoidedAtIsNull(memberId, month));
		if (savings && paid.mmf().signum() > 0) {
			throw new ContributionException("Savings were already paid for this month. Correct that payment first.");
		}
		if (mgr && paid.mgr().signum() > 0) {
			throw new ContributionException("MGR was already paid for this month. Correct that payment first.");
		}
		ContributionOptOut optOut = optOuts.findByMemberIdAndMonth(memberId, month)
				.orElseGet(() -> new ContributionOptOut(member, month));
		optOut.set(savings, mgr, blankToNull(note));
		optOuts.save(optOut);
	}

	public Contribution get(Long id) {
		return contributions.findById(id).orElseThrow(() -> new ContributionException("No such contribution."));
	}

	private MemberMonth build(Member member, YearMonth month, ContributionOptOut optOut, List<Contribution> payments) {
		Portions due = rules.due(member, month, optOut);
		Portions paid = ContributionRules.paidBy(payments);
		MonthStatus status = rules.isMemberIn(member, month)
				? rules.status(month, due, payments, today())
				: MonthStatus.NOT_TRACKED;
		return new MemberMonth(member, month, due, paid, status, optOut, payments);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}

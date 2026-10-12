package com.personal.chama.mgr;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.chama.common.Money;
import com.personal.chama.config.ChamaProperties;
import com.personal.chama.contribution.Contribution;
import com.personal.chama.contribution.ContributionException;
import com.personal.chama.contribution.ContributionOptOut;
import com.personal.chama.contribution.ContributionOptOutRepository;
import com.personal.chama.contribution.ContributionService;
import com.personal.chama.contribution.MemberMonth;
import com.personal.chama.contribution.OptOutPolicy;
import com.personal.chama.contribution.Portions;
import com.personal.chama.decision.DecisionInput;
import com.personal.chama.decision.DecisionService;
import com.personal.chama.decision.GroupDecision;
import com.personal.chama.ledger.LedgerEntry;
import com.personal.chama.ledger.LedgerEntryRepository;
import com.personal.chama.ledger.LedgerService;
import com.personal.chama.member.Member;
import com.personal.chama.member.MemberRepository;
import com.personal.chama.reference.PaymentReference;
import com.personal.chama.reference.ReferenceService;

/**
 * The merry-go-round (brief 4.3, decisions M1-M6 and the Phase 5 plan). Who receives
 * which month is worked out from the round's order and what has happened - payouts made
 * and turns lost through opt-outs - so nothing has to be "advanced" by hand.
 */
@Service
@Transactional(readOnly = true)
public class MgrService implements OptOutPolicy {

	private final MgrRoundRepository rounds;
	private final MgrSlotRepository slots;
	private final MgrPayoutRepository payouts;
	private final MemberRepository members;
	private final ContributionOptOutRepository optOuts;
	private final ContributionService contributions;
	private final LedgerService ledger;
	private final LedgerEntryRepository ledgerEntries;
	private final DecisionService decisions;
	private final AuditorAware<String> auditor;
	private final Clock clock;
	private final YearMonth cutover;
	private final ReferenceService references;

	public MgrService(MgrRoundRepository rounds, MgrSlotRepository slots, MgrPayoutRepository payouts,
			MemberRepository members, ContributionOptOutRepository optOuts, ContributionService contributions,
			LedgerService ledger, LedgerEntryRepository ledgerEntries, DecisionService decisions,
			AuditorAware<String> auditor, Clock clock, ChamaProperties properties, ReferenceService references) {
		this.rounds = rounds;
		this.slots = slots;
		this.payouts = payouts;
		this.members = members;
		this.optOuts = optOuts;
		this.contributions = contributions;
		this.ledger = ledger;
		this.ledgerEntries = ledgerEntries;
		this.decisions = decisions;
		this.auditor = auditor;
		this.clock = clock;
		this.cutover = properties.cutoverMonth();
		this.references = references;
	}

	public YearMonth currentMonth() {
		return YearMonth.now(clock);
	}

	// ---------------------------------------------------------------- the rotation

	public List<RoundView> allRounds() {
		List<MgrPayout> all = payouts.findAllByOrderByRecordedAtAscIdAsc();
		return rounds.findAllByOrderByNumberAsc().stream().map(r -> build(r, all)).toList();
	}

	public Optional<RoundView> currentRound() {
		List<RoundView> all = allRounds();
		return all.isEmpty() ? Optional.empty() : Optional.of(all.get(all.size() - 1));
	}

	/**
	 * Works out each place's month. Places are taken in order from the start month; a
	 * member who opted out of the MGR in any month of the round up to their turn loses it
	 * (M4) and everyone after moves up a month (Phase 5 plan, Q2). A place already paid
	 * keeps the month it was paid for.
	 */
	private RoundView build(MgrRound round, List<MgrPayout> allPayouts) {
		Map<Long, List<MgrPayout>> bySlot = allPayouts.stream().filter(p -> !p.isVoided())
				.collect(Collectors.groupingBy(p -> p.getSlot().getId()));
		Set<String> mgrOptOuts = new HashSet<>();
		for (MgrSlot slot : round.getSlots()) {
			for (ContributionOptOut o : optOuts.findByMemberId(slot.getMember().getId())) {
				if (o.isOptOutMgr()) {
					mgrOptOuts.add(slot.getMember().getId() + ":" + o.getMonth());
				}
			}
		}
		YearMonth current = currentMonth();
		YearMonth next = round.getStartMonth();
		List<SlotView> views = new ArrayList<>();
		for (MgrSlot slot : round.getSlots()) {
			Member member = slot.getMember();
			List<MgrPayout> paid = bySlot.getOrDefault(slot.getId(), List.of());
			if (!paid.isEmpty()) {
				YearMonth month = paid.get(0).getMonth();
				views.add(new SlotView(slot, member, month, SlotStatus.PAID, paid, null));
				next = month.plusMonths(1);
				continue;
			}
			String lostReason = null;
			if (!member.isActive()) {
				lostReason = "no longer active";
			} else {
				for (YearMonth m = round.getStartMonth(); !m.isAfter(next); m = m.plusMonths(1)) {
					if (mgrOptOuts.contains(member.getId() + ":" + m)) {
						lostReason = "opted out of the MGR in " + m;
						break;
					}
				}
				if (lostReason == null && (member.getMgrStartDate() == null
						|| YearMonth.from(member.getMgrStartDate()).isAfter(next))) {
					lostReason = "not in the MGR yet in " + next;
				}
			}
			if (lostReason != null) {
				views.add(new SlotView(slot, member, null, SlotStatus.LOST_TURN, List.of(), lostReason));
				continue;
			}
			SlotStatus status = next.isAfter(current) ? SlotStatus.UPCOMING : SlotStatus.DUE;
			views.add(new SlotView(slot, member, next, status, List.of(), null));
			next = next.plusMonths(1);
		}
		return new RoundView(round, views);
	}

	public Optional<SlotView> slotFor(YearMonth month) {
		return allRounds().stream().flatMap(r -> r.slots().stream())
				.filter(s -> month.equals(s.month())).findFirst();
	}

	public SlotView slot(Long slotId) {
		return allRounds().stream().flatMap(r -> r.slots().stream())
				.filter(s -> s.slot().getId().equals(slotId)).findFirst()
				.orElseThrow(() -> new MgrException("No such place in the rotation."));
	}

	// ---------------------------------------------------------------- rounds

	/** Sets up a round (or changes the order of one with no payouts yet). */
	@Transactional
	public MgrRound saveRound(Long roundId, int number, YearMonth startMonth, List<Long> order) {
		List<Long> ids = order == null ? List.of() : order.stream().filter(id -> id != null).toList();
		if (ids.isEmpty()) {
			throw new MgrException("Put at least one member in the order.");
		}
		if (new HashSet<>(ids).size() != ids.size()) {
			throw new MgrException("Each member can only appear once in a round.");
		}
		List<Member> ordered = new ArrayList<>();
		for (Long id : ids) {
			Member m = members.findById(id).orElseThrow(() -> new MgrException("No such member."));
			if (!m.isActive() || m.getMgrStartDate() == null) {
				throw new MgrException(m.getFullName() + " isn't an active member of the MGR.");
			}
			ordered.add(m);
		}
		if (startMonth == null) {
			throw new MgrException("Choose the month the round starts.");
		}
		List<RoundView> existing = allRounds();
		MgrRound round;
		if (roundId != null) {
			RoundView view = existing.stream().filter(r -> r.round().getId().equals(roundId)).findFirst()
					.orElseThrow(() -> new MgrException("No such round."));
			if (view.slots().stream().anyMatch(s -> !s.payouts().isEmpty())) {
				throw new MgrException("This round already has payouts, so its order can't change.");
			}
			round = rounds.findWithSlotsById(roundId).orElseThrow();
			round.getSlots().clear();
			rounds.saveAndFlush(round);
		} else {
			if (!existing.isEmpty()) {
				RoundView last = existing.get(existing.size() - 1);
				if (!last.isComplete()) {
					throw new MgrException("Round " + last.round().getNumber() + " isn't finished yet.");
				}
				if (number != last.round().getNumber() + 1) {
					throw new MgrException("The next round is Round " + (last.round().getNumber() + 1) + ".");
				}
				if (!startMonth.isAfter(last.lastMonth())) {
					throw new MgrException("The new round must start after " + last.lastMonth() + ".");
				}
			}
			round = rounds.save(new MgrRound(number, startMonth));
		}
		round.setOrder(ordered, startMonth);
		return rounds.save(round);
	}

	// ---------------------------------------------------------------- payouts

	/** Everything the payout screen needs for a place. */
	public PayoutPreview preview(Long slotId) {
		SlotView slot = slot(slotId);
		if (slot.month() == null) {
			throw new MgrException("This place has no month: " + slot.lostReason() + ".");
		}
		YearMonth month = slot.month();
		Member recipient = slot.member();
		List<MemberMonth> rows = contributions.monthOverview(month);
		List<MemberMonth> unpaid = rows.stream()
				.filter(r -> !r.member().getId().equals(recipient.getId()) && r.owed().mgr().signum() > 0).toList();
		MemberMonth own = rows.stream().filter(r -> r.member().getId().equals(recipient.getId())).findFirst()
				.orElse(null);
		BigDecimal ownMgrOwed = own == null ? Money.ZERO : own.owed().mgr();
		BigDecimal ownSavingsOwed = own == null ? Money.ZERO : own.owed().mmf();
		BigDecimal balance = ledger.mgrBalanceFor(month);
		boolean topUp = !slot.payouts().isEmpty();
		BigDecimal gross = topUp ? balance : balance.add(ownMgrOwed);
		return new PayoutPreview(slot, rows, unpaid, topUp ? Money.ZERO : ownMgrOwed,
				topUp ? Money.ZERO : ownSavingsOwed, gross, topUp);
	}

	/**
	 * Pays the month's pool to the recipient (M1, M2, Phase 5 plan Q1-Q4). Their own MGR
	 * part, if unpaid, is netted; their unpaid savings can be deducted. Both are recorded
	 * as their contribution. Everyone else must have paid in, unless a group decision
	 * allows paying out now. A second payout for a month (a top-up) pays MGR money that
	 * came in afterwards.
	 */
	@Transactional
	public MgrPayout payOut(Long slotId, LocalDate paidDate, String method, String reference, boolean deductSavings,
			DecisionInput override) {
		PayoutPreview preview = preview(slotId);
		SlotView slot = preview.slot();
		YearMonth month = slot.month();
		if (month.isBefore(cutover)) {
			throw new MgrException(month + " is before the app took over. Record it as history instead.");
		}
		if (month.isAfter(currentMonth())) {
			throw new MgrException("It isn't " + month + " yet.");
		}
		if (paidDate == null || paidDate.isAfter(LocalDate.now(clock))) {
			throw new MgrException("The payout date can't be in the future.");
		}
		if (method == null || method.isBlank()) {
			throw new MgrException("Enter how the payout was sent.");
		}
		if (preview.gross().signum() <= 0) {
			throw new MgrException("There is no MGR money to pay out for " + month + ".");
		}
		references.checkAvailable(reference, MgrException::new);
		Long decisionId = null;
		if (!preview.topUp() && !preview.unpaid().isEmpty()) {
			if (override == null || override.getHowAgreed() == null || override.getHowAgreed().isBlank()) {
				throw new MgrException("Not everyone has paid their MGR part: "
						+ preview.unpaid().stream().map(r -> r.member().getFullName()).collect(Collectors.joining(", "))
						+ ". The payout waits, unless the group decides to pay out now.");
			}
			decisionId = decisions.record(GroupDecision.Kind.MGR_PAYOUT_EXCEPTION, "MGR for " + month + " paid to "
					+ slot.member().getFullName() + " before everyone paid in", override, null).getId();
		}
		BigDecimal netted = preview.ownMgrOwed();
		BigDecimal deducted = deductSavings ? preview.ownSavingsOwed() : Money.ZERO;
		Long contributionId = null;
		if (netted.add(deducted).signum() > 0) {
			Contribution c = contributions.recordPortions(slot.member().getId(), month, new Portions(deducted, netted),
					paidDate, "MGR payout", "Netted from / deducted at the MGR payout");
			contributionId = c.getId();
		}
		BigDecimal gross = ledger.mgrBalanceFor(month);
		MgrSlot entity = slots.findWithMemberById(slotId).orElseThrow();
		MgrPayout payout = payouts.save(new MgrPayout(entity, slot.member(), month, gross, netted, deducted, paidDate,
				method.strip(), reference == null || reference.isBlank() ? null : reference.strip(), false,
				contributionId, decisionId));
		references.claim(reference, PaymentReference.Kind.MGR_PAYOUT, payout.getId(),
				"MGR payout to " + slot.member().getFullName() + " for "
						+ month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
				MgrException::new);
		ledger.post(LedgerEntry.forMgrPayout(gross, slot.member(), month, paidDate, payout.getId()));
		return payout;
	}

	/** A payout made before the app (e.g. September 2026): kept for the record, not in the ledger. */
	@Transactional
	public MgrPayout recordHistory(Long slotId, BigDecimal amount, LocalDate paidDate) {
		SlotView slot = slot(slotId);
		if (slot.month() == null || !slot.month().isBefore(cutover)) {
			throw new MgrException("Only payouts from before " + cutover + " are recorded as history.");
		}
		if (!slot.payouts().isEmpty()) {
			throw new MgrException("This payout is already recorded.");
		}
		if (amount == null || amount.signum() <= 0 || paidDate == null) {
			throw new MgrException("Enter the amount and date of the payout.");
		}
		MgrSlot entity = slots.findWithMemberById(slotId).orElseThrow();
		return payouts.save(new MgrPayout(entity, slot.member(), slot.month(), Money.orZero(amount), Money.ZERO,
				Money.ZERO, paidDate, "Before the app", null, true, null, null));
	}

	/** Reverses the most recent payout; the rotation goes back to before it. */
	@Transactional
	public void voidPayout(Long payoutId, String reason) {
		List<MgrPayout> standing = payouts.findAllByOrderByRecordedAtAscIdAsc().stream().filter(p -> !p.isVoided())
				.toList();
		if (standing.isEmpty() || !standing.get(standing.size() - 1).getId().equals(payoutId)) {
			throw new MgrException("Only the most recent payout can be corrected.");
		}
		MgrPayout payout = standing.get(standing.size() - 1);
		for (LedgerEntry entry : ledgerEntries.findByMgrPayoutIdAndReversesIdIsNull(payoutId)) {
			if (!ledger.isReversed(entry.getId())) {
				ledger.reverse(entry.getId(), reason);
			}
		}
		if (payout.getContributionId() != null) {
			contributions.voidContribution(payout.getContributionId(), reason);
		}
		payout.voidWith(reason, auditor.getCurrentAuditor().orElse("system"), Instant.now(clock));
		references.release(PaymentReference.Kind.MGR_PAYOUT, payoutId);
	}

	public List<MgrPayout> payoutsTo(Long memberId) {
		return payouts.findByMemberIdAndVoidedAtIsNullOrderByMonthAsc(memberId);
	}

	public Long latestPayoutId() {
		List<MgrPayout> standing = payouts.findAllByOrderByRecordedAtAscIdAsc().stream().filter(p -> !p.isVoided())
				.toList();
		return standing.isEmpty() ? null : standing.get(standing.size() - 1).getId();
	}

	// ---------------------------------------------------------------- opt-out rule (M5)

	/**
	 * Once a member has received their payout in a round, they can't opt out of the MGR
	 * for the rest of it unless the group decides (M5).
	 */
	@Override
	public void checkMgrOptOut(Member member, YearMonth month, DecisionInput decision) {
		for (RoundView round : allRounds()) {
			if (month.isBefore(round.round().getStartMonth())) {
				continue;
			}
			boolean received = round.slots().stream().anyMatch(s -> s.member().getId().equals(member.getId())
					&& s.status() == SlotStatus.PAID && !s.month().isAfter(month));
			boolean withinRound = round.isComplete() ? !month.isAfter(round.lastMonth()) : true;
			if (received && withinRound) {
				if (decision == null || decision.getHowAgreed() == null || decision.getHowAgreed().isBlank()) {
					throw new ContributionException(member.getFullName() + " has already received their payout in Round "
							+ round.round().getNumber() + ", so they can't opt out of the MGR for the rest of it "
							+ "unless the group decides.");
				}
				decisions.record(GroupDecision.Kind.MGR_OPT_OUT_EXCEPTION, member.getFullName()
						+ " allowed to opt out of the MGR for " + month + " after receiving", decision, null);
				return;
			}
		}
	}

	// ---------------------------------------------------------------- views

	public enum SlotStatus {
		PAID, DUE, UPCOMING, LOST_TURN
	}

	public record SlotView(MgrSlot slot, Member member, YearMonth month, SlotStatus status, List<MgrPayout> payouts,
			String lostReason) {

		public BigDecimal gross() {
			return payouts.stream().map(MgrPayout::getGross).reduce(Money.ZERO, BigDecimal::add);
		}

		public BigDecimal net() {
			return payouts.stream().map(MgrPayout::net).reduce(Money.ZERO, BigDecimal::add);
		}
	}

	public record RoundView(MgrRound round, List<SlotView> slots) {

		public List<SlotView> activeSlots() {
			return slots.stream().filter(s -> s.status() != SlotStatus.LOST_TURN).toList();
		}

		public boolean isComplete() {
			return slots.stream().allMatch(s -> s.status() == SlotStatus.PAID || s.status() == SlotStatus.LOST_TURN);
		}

		public YearMonth lastMonth() {
			return activeSlots().stream().map(SlotView::month).max(YearMonth::compareTo)
					.orElse(round.getStartMonth().minusMonths(1));
		}

		/** The place for a month, if any. */
		public Optional<SlotView> forMonth(YearMonth month) {
			return activeSlots().stream().filter(s -> month.equals(s.month())).findFirst();
		}

		/** 1-based position among the places still in the round. */
		public int indexOf(SlotView slot) {
			return activeSlots().indexOf(slot) + 1;
		}

		/** The first place after this month that hasn't been paid. */
		public Optional<SlotView> nextAfter(YearMonth month) {
			return activeSlots().stream().filter(s -> s.month().isAfter(month) && s.status() != SlotStatus.PAID)
					.findFirst();
		}

		public Optional<SlotView> slotOf(Long memberId) {
			return slots.stream().filter(s -> s.member().getId().equals(memberId)).findFirst();
		}
	}

	public record PayoutPreview(SlotView slot, List<MemberMonth> rows, List<MemberMonth> unpaid, BigDecimal ownMgrOwed,
			BigDecimal ownSavingsOwed, BigDecimal gross, boolean topUp) {

		public BigDecimal netIfDeducting() {
			return gross.subtract(ownMgrOwed).subtract(ownSavingsOwed);
		}

		public BigDecimal netWithoutDeducting() {
			return gross.subtract(ownMgrOwed);
		}
	}
}

package com.personal.chama.ledger;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.chama.common.Money;

/**
 * Posts and reverses ledger entries, and works out every balance from them. No
 * balance is ever stored or typed in (brief section 4.2).
 */
@Service
@Transactional(readOnly = true)
public class LedgerService {

	private final LedgerEntryRepository entries;
	private final Clock clock;

	public LedgerService(LedgerEntryRepository entries, Clock clock) {
		this.entries = entries;
		this.clock = clock;
	}

	@Transactional
	public LedgerEntry post(LedgerEntry entry) {
		return entries.save(entry);
	}

	/** Cancels an entry with an opposite one. Both stay in the history. */
	@Transactional
	public LedgerEntry reverse(Long entryId, String reason) {
		LedgerEntry original = entries.findById(entryId)
				.orElseThrow(() -> new IllegalArgumentException("No ledger entry " + entryId));
		if (entries.existsByReversesId(entryId)) {
			throw new IllegalStateException("This entry has already been reversed");
		}
		return entries.save(original.reversal(reason, LocalDate.now(clock)));
	}

	public LedgerEntry get(Long id) {
		return entries.findById(id).orElseThrow(() -> new IllegalArgumentException("No ledger entry " + id));
	}

	public boolean isReversed(Long entryId) {
		return entries.existsByReversesId(entryId);
	}

	/** MMF cash: savings in, earnings in (and, from Phase 3, loan money in and out). */
	public BigDecimal mmfBalance() {
		return Money.orZero(entries.sumByTypes(LedgerEntryType.mmfPoolTypes()));
	}

	public BigDecimal mmfChangeIn(YearMonth month) {
		return Money.orZero(entries.sumByTypesBetween(LedgerEntryType.mmfPoolTypes(), month.atDay(1), month.atEndOfMonth()));
	}

	public BigDecimal sharesOf(Long memberId) {
		return Money.orZero(entries.sumByTypesForMember(LedgerEntryType.shareTypes(), memberId));
	}

	public Map<Long, BigDecimal> sharesByMember() {
		Map<Long, BigDecimal> shares = new HashMap<>();
		for (Object[] row : entries.sumByTypesPerMember(LedgerEntryType.shareTypes())) {
			shares.put((Long) row[0], Money.orZero((BigDecimal) row[1]));
		}
		return shares;
	}

	public BigDecimal mgrCollectedFor(YearMonth month) {
		return Money.orZero(entries.sumByTypesForMonth(LedgerEntryType.mgrPoolTypes(), month));
	}

	/** Entries of these types that have not been reversed. */
	public List<LedgerEntry> standing(List<LedgerEntryType> types) {
		return entries.findStandingByTypes(types);
	}
}

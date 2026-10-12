package com.personal.chama.ledger;

import java.math.BigDecimal;
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

	public LedgerService(LedgerEntryRepository entries) {
		this.entries = entries;
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
		return entries.save(original.reversal(reason));
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

	/** Principal out on all loans: what was lent minus what has come back. */
	public BigDecimal principalOutstanding() {
		return Money.orZero(entries.sumByTypes(LedgerEntryType.loanBookTypes())).negate();
	}

	public BigDecimal principalOutstandingOn(LocalDate date) {
		return Money.orZero(entries.sumByTypesUpTo(LedgerEntryType.loanBookTypes(), date)).negate();
	}

	public BigDecimal principalOutstandingFor(Long loanId) {
		return Money.orZero(entries.sumByTypesForLoan(LedgerEntryType.loanBookTypes(), loanId)).negate();
	}

	public BigDecimal interestPaidFor(Long loanId) {
		return Money.orZero(entries.sumByTypesForLoan(List.of(LedgerEntryType.LOAN_INTEREST_PAID), loanId));
	}

	/** The "total chama fund" used for loan caps and the goal: cash plus principal out on loan (decision P1). */
	public BigDecimal poolTotal() {
		return mmfBalance().add(principalOutstanding());
	}

	public BigDecimal mgrCollectedFor(YearMonth month) {
		return Money.orZero(entries.sumByTypesForMonth(LedgerEntryType.mgrPoolTypes(), month));
	}

	/** Entries of these types that have not been reversed. */
	public List<LedgerEntry> standing(List<LedgerEntryType> types) {
		return entries.findStandingByTypes(types);
	}
}

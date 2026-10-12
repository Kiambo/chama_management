package com.personal.chama.pool;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.chama.common.Money;
import com.personal.chama.config.ChamaProperties;
import com.personal.chama.ledger.LedgerEntry;
import com.personal.chama.ledger.LedgerEntryType;
import com.personal.chama.ledger.LedgerService;
import com.personal.chama.member.Member;
import com.personal.chama.member.MemberRepository;

/**
 * Balances at the cut-over (decision C5). The recorder enters each member's shares and
 * the MMF statement balance on the day before the cut-over month. The group money
 * (past earnings) is what's left:
 *
 * <pre>group money = statement balance + loans out - total of members' shares</pre>
 *
 * Loans out at the cut-over are entered as existing loans (Phase 3); their disbursement
 * entries already lower the ledger's cash, so they are added back here.
 */
@Service
@Transactional(readOnly = true)
public class OpeningBalanceService {

	private static final List<LedgerEntryType> OPENING_TYPES =
			List.of(LedgerEntryType.OPENING_SHARES, LedgerEntryType.OPENING_GROUP_FUNDS);

	private final LedgerService ledger;
	private final MemberRepository members;
	private final ChamaProperties properties;

	public OpeningBalanceService(LedgerService ledger, MemberRepository members, ChamaProperties properties) {
		this.ledger = ledger;
		this.members = members;
		this.properties = properties;
	}

	public LocalDate openingDate() {
		return properties.cutoverMonth().atDay(1).minusDays(1);
	}

	/** Opening entries that are in force (not reversed). Empty until opening balances are recorded. */
	public List<LedgerEntry> current() {
		return ledger.standing(OPENING_TYPES);
	}

	public boolean isRecorded() {
		return !current().isEmpty();
	}

	/** Loans still out at the cut-over. Loans arrive in Phase 3; until then there are none. */
	public BigDecimal loansOutAtOpening() {
		return Money.ZERO;
	}

	public BigDecimal groupMoney(BigDecimal statementBalance, BigDecimal totalShares) {
		return statementBalance.add(loansOutAtOpening()).subtract(totalShares);
	}

	/**
	 * Reverses every opening entry so the figures can be entered again. They are
	 * corrected together because the group money depends on all of the shares.
	 */
	@Transactional
	public void redo(String reason) {
		for (LedgerEntry entry : current()) {
			ledger.reverse(entry.getId(), reason);
		}
	}

	@Transactional
	public void record(Map<Long, BigDecimal> sharesByMember, BigDecimal statementBalance) {
		if (isRecorded()) {
			throw new PoolException("Opening balances are already recorded. Correct an entry in the ledger to change them.");
		}
		if (statementBalance == null || statementBalance.signum() < 0) {
			throw new PoolException("Enter the statement balance.");
		}
		BigDecimal totalShares = Money.ZERO;
		for (BigDecimal shares : sharesByMember.values()) {
			if (shares != null && shares.signum() < 0) {
				throw new PoolException("Shares can't be negative.");
			}
			totalShares = totalShares.add(Money.orZero(shares));
		}
		BigDecimal groupMoney = groupMoney(statementBalance, totalShares);
		if (groupMoney.signum() < 0) {
			throw new PoolException("The members' shares add up to more than the statement balance plus loans out. "
					+ "Check the figures.");
		}

		LocalDate date = openingDate();
		for (Map.Entry<Long, BigDecimal> e : sharesByMember.entrySet()) {
			if (e.getValue() != null && e.getValue().signum() > 0) {
				Member member = members.findById(e.getKey()).orElseThrow();
				ledger.post(LedgerEntry.of(LedgerEntryType.OPENING_SHARES, Money.orZero(e.getValue()), member, null,
						date, "Shares at " + date));
			}
		}
		if (groupMoney.signum() > 0) {
			ledger.post(LedgerEntry.of(LedgerEntryType.OPENING_GROUP_FUNDS, groupMoney, null, null, date,
					"Statement " + Money.format(statementBalance) + " + loans out " + Money.format(loansOutAtOpening())
							+ " - shares " + Money.format(totalShares)));
		}
	}
}

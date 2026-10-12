package com.personal.chama.ledger;

import java.util.Arrays;
import java.util.List;

/**
 * What a ledger entry is, and which balances it moves. Loans add types in Phase 3 and
 * MGR payouts in Phase 5.
 */
public enum LedgerEntryType {

	/** A member's savings at the cut-over date. */
	OPENING_SHARES("Opening shares", true, true, false),
	/** Group money (past earnings) at the cut-over date. Nobody's shares. */
	OPENING_GROUP_FUNDS("Opening group funds", true, false, false),
	/** The savings part of a monthly contribution. */
	CONTRIBUTION_MMF("Contribution to MMF", true, true, false),
	/** The merry-go-round part of a monthly contribution. Passes through, not in the MMF. */
	CONTRIBUTION_MGR("Contribution to MGR", false, false, true),
	/** Earnings on the MMF from the statement. Group money, nobody's shares. */
	FUND_EARNINGS("Fund earnings", true, false, false),
	/** A loan paid out of the pool (negative). The money is then out on loan. */
	LOAN_DISBURSED("Loan disbursed", true, false, false, true),
	/** Principal paid back into the pool. */
	LOAN_PRINCIPAL_REPAID("Loan principal repaid", true, false, false, true),
	/** Loan interest. Goes back into the pool, not to members (brief 4.2). */
	LOAN_INTEREST_PAID("Loan interest paid", true, false, false),
	/** A late fee is now owed. Not cash until paid. */
	LATE_FEE_CHARGED("Late fee charged", false, false, false),
	/** A late fee paid. Goes into the pool, like interest. */
	LATE_FEE_PAID("Late fee paid", true, false, false),
	/** A late fee no longer owed (negative), by group decision. */
	LATE_FEE_WAIVED("Late fee waived", false, false, false),
	/** The borrower's own shares used to pay their loan (negative). Matched by repayment entries. */
	RECOVERY_FROM_SHARES("Recovered from borrower's shares", true, true, false),
	/** A guarantor's shares used to pay the loan they backed (negative). Matched by repayment entries. */
	GUARANTOR_CALL("Called on guarantor's shares", true, true, false),
	/** The borrower repays a guarantor: back into the pool and into the guarantor's shares. */
	GUARANTOR_DEBT_REPAID("Repaid to guarantor", true, true, false),
	/** Principal written off by group decision: no longer out on loan, and no cash came back. */
	LOAN_WRITTEN_OFF("Loan written off", false, false, false, true),
	/** The month's MGR pool paid to the recipient (negative). The MGR side is a pass-through. */
	MGR_PAYOUT("MGR payout", false, false, true);

	private final String label;
	private final boolean mmfPool;
	private final boolean shares;
	private final boolean mgrPool;
	/** Moves principal between cash and "out on loan". */
	private final boolean loanBook;

	LedgerEntryType(String label, boolean mmfPool, boolean shares, boolean mgrPool) {
		this(label, mmfPool, shares, mgrPool, false);
	}

	LedgerEntryType(String label, boolean mmfPool, boolean shares, boolean mgrPool, boolean loanBook) {
		this.label = label;
		this.mmfPool = mmfPool;
		this.shares = shares;
		this.mgrPool = mgrPool;
		this.loanBook = loanBook;
	}

	public String getLabel() {
		return label;
	}

	public static List<LedgerEntryType> mmfPoolTypes() {
		return Arrays.stream(values()).filter(t -> t.mmfPool).toList();
	}

	public static List<LedgerEntryType> shareTypes() {
		return Arrays.stream(values()).filter(t -> t.shares).toList();
	}

	/** Principal out on loan = minus the sum of these entries. */
	public static List<LedgerEntryType> loanBookTypes() {
		return Arrays.stream(values()).filter(t -> t.loanBook).toList();
	}

	public static List<LedgerEntryType> mgrPoolTypes() {
		return Arrays.stream(values()).filter(t -> t.mgrPool).toList();
	}
}

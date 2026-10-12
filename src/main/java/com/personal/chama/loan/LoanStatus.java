package com.personal.chama.loan;

public enum LoanStatus {
	ACTIVE("Active"),
	REPAID("Repaid"),
	/** Phase 4: two missed installments, whole balance due. */
	DEFAULTED("Defaulted"),
	/** Phase 4: repayment plan changed by group decision. */
	RESTRUCTURED("Restructured"),
	/** Phase 4: group decided to write off a pool loss. */
	WRITTEN_OFF("Written off"),
	/** Entered by mistake and reversed. Not in the brief; needed so mistakes can be undone. */
	CANCELLED("Cancelled");

	private final String label;

	LoanStatus(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}

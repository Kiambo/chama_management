package com.personal.chama.loan;

/** How a loan came into the app (decision L8). */
public enum LoanKind {
	/** Disbursed through the app, all rules checked. */
	NEW("New loan"),
	/** Made before the app, all rules checked as of entry. */
	EXISTING_CHECKED("Existing loan, checked against the policy"),
	/** Made before the app on agreed terms; rules not re-checked. */
	EXISTING_AS_AGREED("Existing loan, recorded as agreed");

	private final String label;

	LoanKind(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	public boolean checksRules() {
		return this != EXISTING_AS_AGREED;
	}
}

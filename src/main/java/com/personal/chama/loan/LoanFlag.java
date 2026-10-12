package com.personal.chama.loan;

/** How a loan is doing, for badges. The tone drives the colour. */
public enum LoanFlag {
	ON_TRACK("On track", "green"),
	DUE_SOON("Payment due", "amber"),
	OVERDUE("Overdue", "red"),
	CLOSED("", "grey");

	private final String label;
	private final String tone;

	LoanFlag(String label, String tone) {
		this.label = label;
		this.tone = tone;
	}

	public String getLabel() {
		return label;
	}

	public String getTone() {
		return tone;
	}
}

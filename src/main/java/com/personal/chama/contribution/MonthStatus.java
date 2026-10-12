package com.personal.chama.contribution;

/** A member's contribution status for one month. The tone drives the badge colour. */
public enum MonthStatus {

	PAID("Paid", "green"),
	PAID_LATE("Paid late", "amber"),
	PARTLY_PAID("Partly paid", "amber"),
	/** Nothing paid yet, but the due day has not passed. */
	DUE("Due", "amber"),
	MISSED("Missed", "red"),
	OPTED_OUT("Opted out", "grey"),
	/** Before the member joined, or before the cut-over with nothing recorded. */
	NOT_TRACKED("", "none");

	private final String label;
	private final String tone;

	MonthStatus(String label, String tone) {
		this.label = label;
		this.tone = tone;
	}

	public String getLabel() {
		return label;
	}

	public String getTone() {
		return tone;
	}

	public boolean isComplete() {
		return this == PAID || this == PAID_LATE;
	}
}

package com.personal.chama.contribution;

import java.math.BigDecimal;

import com.personal.chama.common.Money;

/** An amount split into its savings (MMF) and merry-go-round (MGR) parts. */
public record Portions(BigDecimal mmf, BigDecimal mgr) {

	public static final Portions NONE = new Portions(Money.ZERO, Money.ZERO);

	public BigDecimal total() {
		return mmf.add(mgr);
	}

	public Portions plus(Portions other) {
		return new Portions(mmf.add(other.mmf), mgr.add(other.mgr));
	}

	/** What is left of this (due) after what has been paid, never below zero per part. */
	public Portions remainingAfter(Portions paid) {
		return new Portions(mmf.subtract(paid.mmf).max(Money.ZERO), mgr.subtract(paid.mgr).max(Money.ZERO));
	}

	public boolean isZero() {
		return mmf.signum() == 0 && mgr.signum() == 0;
	}
}

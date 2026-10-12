package com.personal.chama.decision;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

/**
 * The group decision that authorises an action: when, how it was agreed (e.g. "WhatsApp
 * vote, 6 of 7"), why, and optionally a screenshot of the vote as evidence.
 */
public class DecisionInput {

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate decidedOn;
	private String howAgreed;
	private String reason;
	private MultipartFile evidence;

	public static DecisionInput of(LocalDate decidedOn, String howAgreed, String reason) {
		DecisionInput input = new DecisionInput();
		input.decidedOn = decidedOn;
		input.howAgreed = howAgreed;
		input.reason = reason;
		return input;
	}

	public LocalDate getDecidedOn() {
		return decidedOn;
	}

	public void setDecidedOn(LocalDate decidedOn) {
		this.decidedOn = decidedOn;
	}

	public String getHowAgreed() {
		return howAgreed;
	}

	public void setHowAgreed(String howAgreed) {
		this.howAgreed = howAgreed;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public MultipartFile getEvidence() {
		return evidence;
	}

	public void setEvidence(MultipartFile evidence) {
		this.evidence = evidence;
	}
}

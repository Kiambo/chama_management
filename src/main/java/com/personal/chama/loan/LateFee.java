package com.personal.chama.loan;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The one late fee on a missed installment (Phase 4 plan, Q1). */
@Entity
@Table(name = "late_fee")
@EntityListeners(AuditingEntityListener.class)
public class LateFee {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "loan_id", nullable = false)
	private Long loanId;

	@Column(name = "installment_id", nullable = false)
	private Long installmentId;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal amount;

	/** From this date the fee is owed, and payments clear it first. */
	@Column(name = "charged_on", nullable = false)
	private LocalDate chargedOn;

	@Column(name = "waived_amount", nullable = false, precision = 14, scale = 2)
	private BigDecimal waivedAmount = BigDecimal.ZERO.setScale(2);

	@Column(name = "waived_decision_id")
	private Long waivedDecisionId;

	@Column(nullable = false)
	private boolean cancelled;

	@CreatedDate
	@Column(name = "recorded_at", nullable = false, updatable = false)
	private Instant recordedAt;

	@CreatedBy
	@Column(name = "recorded_by", nullable = false, updatable = false, length = 50)
	private String recordedBy;

	protected LateFee() {
	}

	LateFee(Long loanId, Long installmentId, BigDecimal amount, LocalDate chargedOn) {
		this.loanId = loanId;
		this.installmentId = installmentId;
		this.amount = amount;
		this.chargedOn = chargedOn;
	}

	void waive(BigDecimal unpaid, Long decisionId) {
		this.waivedAmount = waivedAmount.add(unpaid);
		this.waivedDecisionId = decisionId;
	}

	void cancel() {
		this.cancelled = true;
	}

	/** What the borrower has to pay for this fee: the amount less anything waived. */
	public BigDecimal payable() {
		return amount.subtract(waivedAmount);
	}

	public boolean isWaived() {
		return waivedAmount.signum() > 0;
	}

	public Long getId() {
		return id;
	}

	public Long getLoanId() {
		return loanId;
	}

	public Long getInstallmentId() {
		return installmentId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public LocalDate getChargedOn() {
		return chargedOn;
	}

	public BigDecimal getWaivedAmount() {
		return waivedAmount;
	}

	public Long getWaivedDecisionId() {
		return waivedDecisionId;
	}

	public boolean isCancelled() {
		return cancelled;
	}
}

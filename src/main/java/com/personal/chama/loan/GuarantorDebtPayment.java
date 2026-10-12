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

/** The borrower paying back a guarantor. The money goes into the pool and the guarantor's shares. */
@Entity
@Table(name = "guarantor_debt_payment")
@EntityListeners(AuditingEntityListener.class)
public class GuarantorDebtPayment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "debt_id", nullable = false)
	private Long debtId;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal amount;

	@Column(name = "paid_date", nullable = false)
	private LocalDate paidDate;

	@Column(nullable = false, length = 50)
	private String method;

	@Column(length = 100)
	private String reference;

	@CreatedDate
	@Column(name = "recorded_at", nullable = false, updatable = false)
	private Instant recordedAt;

	@CreatedBy
	@Column(name = "recorded_by", nullable = false, updatable = false, length = 50)
	private String recordedBy;

	@Column(name = "voided_at")
	private Instant voidedAt;

	@Column(name = "voided_by", length = 50)
	private String voidedBy;

	@Column(name = "void_reason", length = 500)
	private String voidReason;

	protected GuarantorDebtPayment() {
	}

	GuarantorDebtPayment(Long debtId, BigDecimal amount, LocalDate paidDate, String method, String reference) {
		this.debtId = debtId;
		this.amount = amount;
		this.paidDate = paidDate;
		this.method = method;
		this.reference = reference;
	}

	void voidWith(String reason, String by, Instant at) {
		this.voidReason = reason;
		this.voidedBy = by;
		this.voidedAt = at;
	}

	public boolean isVoided() {
		return voidedAt != null;
	}

	public Long getId() {
		return id;
	}

	public Long getDebtId() {
		return debtId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public LocalDate getPaidDate() {
		return paidDate;
	}

	public String getMethod() {
		return method;
	}

	public String getReference() {
		return reference;
	}

	public String getVoidReason() {
		return voidReason;
	}
}

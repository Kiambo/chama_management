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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A payment towards a loan. Never deleted: a mistake is voided and its ledger entries reversed. */
@Entity
@Table(name = "repayment")
@EntityListeners(AuditingEntityListener.class)
public class Repayment {

	/** Where the money came from: the borrower, or recovery from shares (policy section 4). */
	public enum Source {
		BORROWER("Borrower"), BORROWER_SHARES("From borrower's shares"), GUARANTOR_SHARES("From guarantor's shares");

		private final String label;

		Source(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "loan_id")
	private Loan loan;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal amount;

	@Column(name = "paid_date", nullable = false)
	private LocalDate paidDate;

	@Column(nullable = false, length = 50)
	private String method;

	@Column(length = 100)
	private String reference;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Source source = Source.BORROWER;

	/** For GUARANTOR_SHARES: whose shares. */
	@Column(name = "guarantor_id")
	private Long guarantorId;

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

	protected Repayment() {
	}

	Repayment(Loan loan, BigDecimal amount, LocalDate paidDate, String method, String reference) {
		this.loan = loan;
		this.amount = amount;
		this.paidDate = paidDate;
		this.method = method;
		this.reference = reference;
	}

	static Repayment recovery(Loan loan, BigDecimal amount, LocalDate date, Source source, Long guarantorId) {
		Repayment r = new Repayment(loan, amount, date, "Recovery", null);
		r.source = source;
		r.guarantorId = guarantorId;
		return r;
	}

	public Source getSource() {
		return source;
	}

	public Long getGuarantorId() {
		return guarantorId;
	}

	public boolean isRecovery() {
		return source != Source.BORROWER;
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

	public Loan getLoan() {
		return loan;
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

	public Instant getVoidedAt() {
		return voidedAt;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}

	public String getRecordedBy() {
		return recordedBy;
	}

	public String getVoidReason() {
		return voidReason;
	}
}

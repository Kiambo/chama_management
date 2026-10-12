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

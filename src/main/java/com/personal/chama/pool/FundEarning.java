package com.personal.chama.pool;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Earnings on the MMF for a month, from the statement (decision C6). Group money: it
 * raises the pool but nobody's shares. The statement file can be attached as evidence.
 */
@Entity
@Table(name = "fund_earning")
@EntityListeners(AuditingEntityListener.class)
public class FundEarning {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "period_month", nullable = false)
	private YearMonth month;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal amount;

	@Column(name = "statement_reference", length = 100)
	private String statementReference;

	@Column(name = "statement_file_name")
	private String statementFileName;

	@Column(name = "statement_content_type", length = 100)
	private String statementContentType;

	@Basic(fetch = FetchType.LAZY)
	@Column(name = "statement_file")
	private byte[] statementFile;

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

	protected FundEarning() {
	}

	public FundEarning(YearMonth month, BigDecimal amount, String statementReference) {
		this.month = month;
		this.amount = amount;
		this.statementReference = statementReference;
	}

	void attachStatement(String fileName, String contentType, byte[] content) {
		this.statementFileName = fileName;
		this.statementContentType = contentType;
		this.statementFile = content;
	}

	void voidWith(String reason, String by, Instant at) {
		if (voidedAt != null) {
			throw new IllegalStateException("These earnings have already been corrected.");
		}
		this.voidReason = reason;
		this.voidedBy = by;
		this.voidedAt = at;
	}

	public boolean hasStatement() {
		return statementFileName != null;
	}

	public boolean isVoided() {
		return voidedAt != null;
	}

	public Long getId() {
		return id;
	}

	public YearMonth getMonth() {
		return month;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public String getStatementReference() {
		return statementReference;
	}

	public String getStatementFileName() {
		return statementFileName;
	}

	public String getStatementContentType() {
		return statementContentType;
	}

	public byte[] getStatementFile() {
		return statementFile;
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

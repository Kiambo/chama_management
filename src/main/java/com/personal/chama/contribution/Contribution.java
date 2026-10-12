package com.personal.chama.contribution;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.personal.chama.member.Member;

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

/**
 * One payment towards a member's month. A month can be paid in several payments.
 * Never deleted: a mistake is voided, which reverses its ledger entries.
 */
@Entity
@Table(name = "contribution")
@EntityListeners(AuditingEntityListener.class)
public class Contribution {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id")
	private Member member;

	@Column(name = "period_month", nullable = false)
	private YearMonth month;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal amount;

	@Column(name = "mmf_portion", nullable = false, precision = 14, scale = 2)
	private BigDecimal mmfPortion;

	@Column(name = "mgr_portion", nullable = false, precision = 14, scale = 2)
	private BigDecimal mgrPortion;

	@Column(name = "paid_date", nullable = false)
	private LocalDate paidDate;

	@Column(nullable = false, length = 50)
	private String method;

	@Column(length = 100)
	private String reference;

	@Column(name = "agreed_backpay", nullable = false)
	private boolean agreedBackpay;

	@Column(length = 500)
	private String note;

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

	protected Contribution() {
	}

	public Contribution(Member member, YearMonth month, Portions split, LocalDate paidDate, String method,
			String reference, boolean agreedBackpay, String note) {
		this.member = member;
		this.month = month;
		this.mmfPortion = split.mmf();
		this.mgrPortion = split.mgr();
		this.amount = split.total();
		this.paidDate = paidDate;
		this.method = method;
		this.reference = reference;
		this.agreedBackpay = agreedBackpay;
		this.note = note;
	}

	void voidWith(String reason, String by, Instant at) {
		if (isVoided()) {
			throw new ContributionException("This contribution has already been corrected.");
		}
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

	public Member getMember() {
		return member;
	}

	public YearMonth getMonth() {
		return month;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public BigDecimal getMmfPortion() {
		return mmfPortion;
	}

	public BigDecimal getMgrPortion() {
		return mgrPortion;
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

	public boolean isAgreedBackpay() {
		return agreedBackpay;
	}

	public String getNote() {
		return note;
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

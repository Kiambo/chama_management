package com.personal.chama.mgr;

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
 * Money paid to the month's recipient. Gross is the month's MGR pool; the recipient's own
 * MGR part (netted) and any unpaid savings (deducted) are taken off what is sent, and are
 * recorded as their contribution. Never deleted: a mistake is voided and reversed.
 */
@Entity
@Table(name = "mgr_payout")
@EntityListeners(AuditingEntityListener.class)
public class MgrPayout {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "slot_id")
	private MgrSlot slot;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id")
	private Member member;

	@Column(name = "period_month", nullable = false)
	private YearMonth month;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal gross;

	@Column(name = "netted_own_mgr", nullable = false, precision = 14, scale = 2)
	private BigDecimal nettedOwnMgr;

	@Column(name = "savings_deducted", nullable = false, precision = 14, scale = 2)
	private BigDecimal savingsDeducted;

	@Column(name = "paid_date", nullable = false)
	private LocalDate paidDate;

	@Column(nullable = false, length = 50)
	private String method;

	@Column(length = 100)
	private String reference;

	@Column(nullable = false)
	private boolean historical;

	@Column(name = "contribution_id")
	private Long contributionId;

	@Column(name = "decision_id")
	private Long decisionId;

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

	protected MgrPayout() {
	}

	MgrPayout(MgrSlot slot, Member member, YearMonth month, BigDecimal gross, BigDecimal nettedOwnMgr,
			BigDecimal savingsDeducted, LocalDate paidDate, String method, String reference, boolean historical,
			Long contributionId, Long decisionId) {
		this.slot = slot;
		this.member = member;
		this.month = month;
		this.gross = gross;
		this.nettedOwnMgr = nettedOwnMgr;
		this.savingsDeducted = savingsDeducted;
		this.paidDate = paidDate;
		this.method = method;
		this.reference = reference;
		this.historical = historical;
		this.contributionId = contributionId;
		this.decisionId = decisionId;
	}

	void voidWith(String reason, String by, Instant at) {
		this.voidReason = reason;
		this.voidedBy = by;
		this.voidedAt = at;
	}

	/** What was actually sent to the recipient. */
	public BigDecimal net() {
		return gross.subtract(nettedOwnMgr).subtract(savingsDeducted);
	}

	public boolean isVoided() {
		return voidedAt != null;
	}

	public Long getId() {
		return id;
	}

	public MgrSlot getSlot() {
		return slot;
	}

	public Member getMember() {
		return member;
	}

	public YearMonth getMonth() {
		return month;
	}

	public BigDecimal getGross() {
		return gross;
	}

	public BigDecimal getNettedOwnMgr() {
		return nettedOwnMgr;
	}

	public BigDecimal getSavingsDeducted() {
		return savingsDeducted;
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

	public boolean isHistorical() {
		return historical;
	}

	public Long getContributionId() {
		return contributionId;
	}

	public Long getDecisionId() {
		return decisionId;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}

	public String getVoidReason() {
		return voidReason;
	}
}

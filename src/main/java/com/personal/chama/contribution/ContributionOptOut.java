package com.personal.chama.contribution;

import java.time.Instant;
import java.time.YearMonth;

import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
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

/** A member's opt-out of savings and/or MGR for one month (decision C3). */
@Entity
@Table(name = "contribution_opt_out")
@EntityListeners(AuditingEntityListener.class)
public class ContributionOptOut {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id")
	private Member member;

	@Column(name = "period_month", nullable = false)
	private YearMonth month;

	@Column(name = "opt_out_savings", nullable = false)
	private boolean optOutSavings;

	@Column(name = "opt_out_mgr", nullable = false)
	private boolean optOutMgr;

	@Column(length = 500)
	private String note;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@LastModifiedBy
	@Column(name = "updated_by", nullable = false, length = 50)
	private String updatedBy;

	protected ContributionOptOut() {
	}

	public ContributionOptOut(Member member, YearMonth month) {
		this.member = member;
		this.month = month;
	}

	void set(boolean optOutSavings, boolean optOutMgr, String note) {
		this.optOutSavings = optOutSavings;
		this.optOutMgr = optOutMgr;
		this.note = note;
	}

	public boolean isOptOutSavings() {
		return optOutSavings;
	}

	public boolean isOptOutMgr() {
		return optOutMgr;
	}

	public boolean isAny() {
		return optOutSavings || optOutMgr;
	}

	public String getNote() {
		return note;
	}

	public YearMonth getMonth() {
		return month;
	}

	public Member getMember() {
		return member;
	}
}

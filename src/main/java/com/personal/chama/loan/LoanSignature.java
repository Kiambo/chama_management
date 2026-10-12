package com.personal.chama.loan;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.personal.chama.member.Member;

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

/**
 * A party's initials on the digital loan agreement. In v1 the recorder enters them after
 * the party has confirmed; recordedBy shows who entered them.
 */
@Entity
@Table(name = "loan_signature")
@EntityListeners(AuditingEntityListener.class)
public class LoanSignature {

	public enum Role {
		BORROWER, GUARANTOR, TREASURER
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "loan_id")
	private Loan loan;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "member_id")
	private Member member;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, length = 10)
	private String initials;

	@Column(name = "signed_date", nullable = false)
	private LocalDate signedDate;

	@CreatedDate
	@Column(name = "recorded_at", nullable = false, updatable = false)
	private Instant recordedAt;

	@CreatedBy
	@Column(name = "recorded_by", nullable = false, updatable = false, length = 50)
	private String recordedBy;

	protected LoanSignature() {
	}

	LoanSignature(Loan loan, Role role, Member member, String name, String initials, LocalDate signedDate) {
		this.loan = loan;
		this.role = role;
		this.member = member;
		this.name = name;
		this.initials = initials;
		this.signedDate = signedDate;
	}

	public Long getMemberId() {
		return member == null ? null : member.getId();
	}

	public Role getRole() {
		return role;
	}

	public String getName() {
		return name;
	}

	public String getInitials() {
		return initials;
	}

	public LocalDate getSignedDate() {
		return signedDate;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}

	public String getRecordedBy() {
		return recordedBy;
	}
}

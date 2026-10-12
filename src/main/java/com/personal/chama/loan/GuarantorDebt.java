package com.personal.chama.loan;

import java.math.BigDecimal;

import com.personal.chama.member.Member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * What a borrower owes a guarantor whose shares covered part of their loan (decision R6).
 * Repaying it restores the guarantor's shares.
 */
@Entity
@Table(name = "guarantor_debt")
public class GuarantorDebt {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "loan_id")
	private Loan loan;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "guarantor_id")
	private Member guarantor;

	/** The recovery that created this debt. */
	@Column(name = "repayment_id", nullable = false)
	private Long repaymentId;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal amount;

	protected GuarantorDebt() {
	}

	GuarantorDebt(Loan loan, Member guarantor, Long repaymentId, BigDecimal amount) {
		this.loan = loan;
		this.guarantor = guarantor;
		this.repaymentId = repaymentId;
		this.amount = amount;
	}

	public Long getId() {
		return id;
	}

	public Loan getLoan() {
		return loan;
	}

	public Member getGuarantor() {
		return guarantor;
	}

	public Long getRepaymentId() {
		return repaymentId;
	}

	public BigDecimal getAmount() {
		return amount;
	}
}

package com.personal.chama.loan;

import com.personal.chama.member.Member;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A member pledging their shares behind a loan. Active while the loan is active. */
@Entity
@Table(name = "guarantee")
public class Guarantee {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "loan_id")
	private Loan loan;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "guarantor_id")
	private Member guarantor;

	protected Guarantee() {
	}

	Guarantee(Loan loan, Member guarantor) {
		this.loan = loan;
		this.guarantor = guarantor;
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
}

package com.personal.chama.loan;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuarantorDebtRepository extends JpaRepository<GuarantorDebt, Long> {

	@EntityGraph(attributePaths = { "guarantor", "loan", "loan.borrower" })
	List<GuarantorDebt> findByLoanIdOrderByIdAsc(Long loanId);

	@EntityGraph(attributePaths = { "guarantor", "loan", "loan.borrower" })
	List<GuarantorDebt> findByGuarantorIdOrderByIdAsc(Long guarantorId);

	@EntityGraph(attributePaths = { "guarantor", "loan", "loan.borrower" })
	List<GuarantorDebt> findByLoanBorrowerIdOrderByIdAsc(Long borrowerId);

	List<GuarantorDebt> findByRepaymentId(Long repaymentId);
}

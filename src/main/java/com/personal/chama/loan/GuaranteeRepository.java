package com.personal.chama.loan;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuaranteeRepository extends JpaRepository<Guarantee, Long> {

	@EntityGraph(attributePaths = "guarantor")
	List<Guarantee> findByLoanId(Long loanId);

	/** Guarantees this member has given on loans that are still active. */
	@Query("""
			select g from Guarantee g join fetch g.loan l join fetch l.borrower
			where g.guarantor.id = :memberId and l.status = com.personal.chama.loan.LoanStatus.ACTIVE""")
	List<Guarantee> findActiveByGuarantor(@Param("memberId") Long memberId);
}

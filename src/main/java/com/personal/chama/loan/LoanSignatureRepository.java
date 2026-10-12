package com.personal.chama.loan;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanSignatureRepository extends JpaRepository<LoanSignature, Long> {

	List<LoanSignature> findByLoanId(Long loanId);
}

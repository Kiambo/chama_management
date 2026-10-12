package com.personal.chama.loan;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RepaymentRepository extends JpaRepository<Repayment, Long> {

	/** Standing repayments in the order they are applied: oldest payment first. */
	List<Repayment> findByLoanIdAndVoidedAtIsNullOrderByPaidDateAscIdAsc(Long loanId);

	List<Repayment> findByLoanIdOrderByPaidDateDescIdDesc(Long loanId);
}

package com.personal.chama.loan;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {

	/** Every installment the loan has ever had, including superseded ones, in due-date order. */
	List<Installment> findByLoanIdOrderByDueDateAscIdAsc(Long loanId);
}

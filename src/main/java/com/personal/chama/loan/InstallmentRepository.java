package com.personal.chama.loan;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {

	List<Installment> findByLoanIdOrderByNumber(Long loanId);
}

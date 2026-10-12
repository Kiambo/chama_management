package com.personal.chama.loan;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, Long> {

	@EntityGraph(attributePaths = "borrower")
	Optional<Loan> findWithBorrowerById(Long id);

	@EntityGraph(attributePaths = "borrower")
	List<Loan> findByStatusOrderByDisbursedDateAsc(LoanStatus status);

	@EntityGraph(attributePaths = "borrower")
	List<Loan> findAllByOrderByDisbursedDateDescIdDesc();

	@EntityGraph(attributePaths = "borrower")
	List<Loan> findByBorrowerIdOrderByDisbursedDateDesc(Long borrowerId);

	Optional<Loan> findFirstByBorrowerIdAndStatus(Long borrowerId, LoanStatus status);
}

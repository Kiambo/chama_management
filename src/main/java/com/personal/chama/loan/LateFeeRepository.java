package com.personal.chama.loan;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LateFeeRepository extends JpaRepository<LateFee, Long> {

	List<LateFee> findByLoanIdAndCancelledFalseOrderByChargedOnAscIdAsc(Long loanId);
}

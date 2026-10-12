package com.personal.chama.loan;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleChangeRepository extends JpaRepository<ScheduleChange, Long> {

	List<ScheduleChange> findByLoanIdOrderByEffectiveDateAscIdAsc(Long loanId);
}

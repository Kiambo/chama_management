package com.personal.chama.decision;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupDecisionRepository extends JpaRepository<GroupDecision, Long> {

	List<GroupDecision> findAllByOrderByDecidedOnDescIdDesc();

	List<GroupDecision> findByLoanIdOrderByDecidedOnDescIdDesc(Long loanId);
}

package com.personal.chama.contribution;

import java.time.YearMonth;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContributionRepository extends JpaRepository<Contribution, Long> {

	List<Contribution> findByMemberIdAndMonthAndVoidedAtIsNull(Long memberId, YearMonth month);

	List<Contribution> findByMonthAndVoidedAtIsNull(YearMonth month);

	List<Contribution> findByMemberIdAndVoidedAtIsNullOrderByPaidDateAsc(Long memberId);

	List<Contribution> findByMemberIdOrderByMonthDescPaidDateDesc(Long memberId);
}

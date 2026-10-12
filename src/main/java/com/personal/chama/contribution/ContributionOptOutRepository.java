package com.personal.chama.contribution;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContributionOptOutRepository extends JpaRepository<ContributionOptOut, Long> {

	Optional<ContributionOptOut> findByMemberIdAndMonth(Long memberId, YearMonth month);

	List<ContributionOptOut> findByMonth(YearMonth month);

	List<ContributionOptOut> findByMemberId(Long memberId);
}

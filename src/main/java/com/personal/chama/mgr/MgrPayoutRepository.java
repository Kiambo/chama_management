package com.personal.chama.mgr;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MgrPayoutRepository extends JpaRepository<MgrPayout, Long> {

	@EntityGraph(attributePaths = "member")
	List<MgrPayout> findAllByOrderByRecordedAtAscIdAsc();

	@EntityGraph(attributePaths = "member")
	List<MgrPayout> findByMemberIdAndVoidedAtIsNullOrderByMonthAsc(Long memberId);
}

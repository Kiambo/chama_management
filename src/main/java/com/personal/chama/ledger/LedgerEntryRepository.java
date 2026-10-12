package com.personal.chama.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

	@Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.type in :types")
	BigDecimal sumByTypes(@Param("types") Collection<LedgerEntryType> types);

	@Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.type in :types and e.member.id = :memberId")
	BigDecimal sumByTypesForMember(@Param("types") Collection<LedgerEntryType> types, @Param("memberId") Long memberId);

	@Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.type in :types and e.month = :month")
	BigDecimal sumByTypesForMonth(@Param("types") Collection<LedgerEntryType> types, @Param("month") YearMonth month);

	@Query("""
			select coalesce(sum(e.amount), 0) from LedgerEntry e
			where e.type in :types and e.effectiveDate between :from and :to""")
	BigDecimal sumByTypesBetween(@Param("types") Collection<LedgerEntryType> types,
			@Param("from") LocalDate from, @Param("to") LocalDate to);

	@Query("select e.member.id, coalesce(sum(e.amount), 0) from LedgerEntry e where e.type in :types and e.member is not null group by e.member.id")
	List<Object[]> sumByTypesPerMember(@Param("types") Collection<LedgerEntryType> types);

	boolean existsByReversesId(Long reversesId);

	List<LedgerEntry> findByContributionIdAndReversesIdIsNull(Long contributionId);

	List<LedgerEntry> findByFundEarningIdAndReversesIdIsNull(Long fundEarningId);

	@EntityGraph(attributePaths = "member")
	@Query("select e from LedgerEntry e where e.type in :types and e.reversesId is null and not exists (select r from LedgerEntry r where r.reversesId = e.id)")
	List<LedgerEntry> findStandingByTypes(@Param("types") Collection<LedgerEntryType> types);

	@EntityGraph(attributePaths = "member")
	@Query(value = """
			select e from LedgerEntry e left join e.member m
			where (:memberId is null or m.id = :memberId)
			  and (:type is null or e.type = :type)
			order by e.recordedAt desc, e.id desc""",
			countQuery = """
			select count(e) from LedgerEntry e left join e.member m
			where (:memberId is null or m.id = :memberId)
			  and (:type is null or e.type = :type)""")
	Page<LedgerEntry> search(@Param("memberId") Long memberId, @Param("type") LedgerEntryType type, Pageable pageable);

	@Query("select e.reversesId from LedgerEntry e where e.reversesId in :ids")
	List<Long> findReversedAmong(@Param("ids") Collection<Long> ids);
}

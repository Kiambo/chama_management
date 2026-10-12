package com.personal.chama.member;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface MemberRepository extends JpaRepository<Member, Long> {

	List<Member> findAllByOrderByActiveDescFullNameAsc();

	List<Member> findByActiveTrueOrderByFullNameAsc();

	/** Locks the member row so two payments for the same member can't both pass the "owed" check. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from Member m where m.id = :id")
	Optional<Member> findByIdForUpdate(Long id);

	boolean existsByFullNameIgnoreCase(String fullName);

	boolean existsByFullNameIgnoreCaseAndIdNot(String fullName, Long id);
}

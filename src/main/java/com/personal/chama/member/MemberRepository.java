package com.personal.chama.member;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

	List<Member> findAllByOrderByActiveDescFullNameAsc();

	boolean existsByFullNameIgnoreCase(String fullName);

	boolean existsByFullNameIgnoreCaseAndIdNot(String fullName, Long id);
}

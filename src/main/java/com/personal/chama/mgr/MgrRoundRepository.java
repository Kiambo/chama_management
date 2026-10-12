package com.personal.chama.mgr;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MgrRoundRepository extends JpaRepository<MgrRound, Long> {

	@EntityGraph(attributePaths = { "slots", "slots.member" })
	List<MgrRound> findAllByOrderByNumberAsc();

	@EntityGraph(attributePaths = { "slots", "slots.member" })
	Optional<MgrRound> findWithSlotsById(Long id);
}

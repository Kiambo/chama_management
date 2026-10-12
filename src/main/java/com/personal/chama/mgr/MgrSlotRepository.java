package com.personal.chama.mgr;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MgrSlotRepository extends JpaRepository<MgrSlot, Long> {

	@EntityGraph(attributePaths = { "member", "round" })
	Optional<MgrSlot> findWithMemberById(Long id);
}

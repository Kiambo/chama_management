package com.personal.chama.settings;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

	List<PaymentMethod> findAllByOrderByPositionAscIdAsc();

	boolean existsByNameIgnoreCase(String name);
}

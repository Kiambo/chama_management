package com.personal.chama.pool;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FundEarningRepository extends JpaRepository<FundEarning, Long> {

	List<FundEarning> findAllByOrderByMonthDescIdDesc();
}

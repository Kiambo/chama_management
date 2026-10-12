package com.personal.chama.settings;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingValueRepository extends JpaRepository<SettingValue, Long> {

	List<SettingValue> findAllByOrderByEffectiveFromAscIdAsc();
}

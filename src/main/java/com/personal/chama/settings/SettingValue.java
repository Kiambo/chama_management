package com.personal.chama.settings;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A setting's value from a date onwards, decided by the group. Never changed once saved. */
@Entity
@Table(name = "setting_value")
@EntityListeners(AuditingEntityListener.class)
public class SettingValue {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "setting_key", nullable = false, length = 60)
	private SettingKey key;

	@Column(nullable = false, precision = 14, scale = 4)
	private BigDecimal value;

	@Column(name = "effective_from", nullable = false)
	private LocalDate effectiveFrom;

	@Column(name = "decision_id", nullable = false)
	private Long decisionId;

	@CreatedDate
	@Column(name = "recorded_at", nullable = false, updatable = false)
	private Instant recordedAt;

	@CreatedBy
	@Column(name = "recorded_by", nullable = false, updatable = false, length = 50)
	private String recordedBy;

	protected SettingValue() {
	}

	SettingValue(SettingKey key, BigDecimal value, LocalDate effectiveFrom, Long decisionId) {
		this.key = key;
		this.value = value;
		this.effectiveFrom = effectiveFrom;
		this.decisionId = decisionId;
	}

	public Long getId() {
		return id;
	}

	public SettingKey getKey() {
		return key;
	}

	public BigDecimal getValue() {
		return value;
	}

	public LocalDate getEffectiveFrom() {
		return effectiveFrom;
	}

	public Long getDecisionId() {
		return decisionId;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}

	public String getRecordedBy() {
		return recordedBy;
	}
}

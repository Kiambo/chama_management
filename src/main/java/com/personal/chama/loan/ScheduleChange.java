package com.personal.chama.loan;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A new plan for what is left of a loan, agreed by the group: an extension, a schedule
 * change or a restructure. From its effective date the unpaid installments are replaced
 * by new ones; the old ones are kept, marked superseded.
 */
@Entity
@Table(name = "schedule_change")
@EntityListeners(AuditingEntityListener.class)
public class ScheduleChange {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "loan_id", nullable = false)
	private Long loanId;

	@Column(name = "decision_id", nullable = false)
	private Long decisionId;

	@Column(name = "effective_date", nullable = false)
	private LocalDate effectiveDate;

	/** Interest added by the change: the tier difference for an extension, or an agreed charge. */
	@Column(name = "extra_interest", nullable = false, precision = 14, scale = 2)
	private BigDecimal extraInterest;

	@CreatedDate
	@Column(name = "recorded_at", nullable = false, updatable = false)
	private Instant recordedAt;

	@CreatedBy
	@Column(name = "recorded_by", nullable = false, updatable = false, length = 50)
	private String recordedBy;

	protected ScheduleChange() {
	}

	ScheduleChange(Long loanId, Long decisionId, LocalDate effectiveDate, BigDecimal extraInterest) {
		this.loanId = loanId;
		this.decisionId = decisionId;
		this.effectiveDate = effectiveDate;
		this.extraInterest = extraInterest;
	}

	public Long getId() {
		return id;
	}

	public Long getLoanId() {
		return loanId;
	}

	public Long getDecisionId() {
		return decisionId;
	}

	public LocalDate getEffectiveDate() {
		return effectiveDate;
	}

	public BigDecimal getExtraInterest() {
		return extraInterest;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}
}

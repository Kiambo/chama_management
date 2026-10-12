package com.personal.chama.loan;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One scheduled payment. What has been paid towards it is worked out from repayments. */
@Entity
@Table(name = "installment")
public class Installment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "loan_id")
	private Loan loan;

	@Column(nullable = false)
	private int number;

	@Column(name = "due_date", nullable = false)
	private LocalDate dueDate;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal amount;

	@Column(name = "interest_part", nullable = false, precision = 14, scale = 2)
	private BigDecimal interestPart;

	@Column(name = "principal_part", nullable = false, precision = 14, scale = 2)
	private BigDecimal principalPart;

	/** Null for the original schedule; otherwise the change that created this installment. */
	@Column(name = "schedule_change_id")
	private Long scheduleChangeId;

	/** Set when a later change replaced this installment. Kept for the history. */
	@Column(name = "superseded_by_change_id")
	private Long supersededByChangeId;

	protected Installment() {
	}

	Installment(Loan loan, LoanCalculator.PlannedInstallment planned, Long scheduleChangeId) {
		this(loan, planned);
		this.scheduleChangeId = scheduleChangeId;
	}

	void supersede(Long changeId) {
		this.supersededByChangeId = changeId;
	}

	public boolean isSuperseded() {
		return supersededByChangeId != null;
	}

	public Long getScheduleChangeId() {
		return scheduleChangeId;
	}

	public Long getSupersededByChangeId() {
		return supersededByChangeId;
	}

	Installment(Loan loan, LoanCalculator.PlannedInstallment planned) {
		this.loan = loan;
		this.number = planned.number();
		this.dueDate = planned.dueDate();
		this.amount = planned.amount();
		this.interestPart = planned.interestPart();
		this.principalPart = planned.principalPart();
	}

	public Long getId() {
		return id;
	}

	public int getNumber() {
		return number;
	}

	public LocalDate getDueDate() {
		return dueDate;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public BigDecimal getInterestPart() {
		return interestPart;
	}

	public BigDecimal getPrincipalPart() {
		return principalPart;
	}
}

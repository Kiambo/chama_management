package com.personal.chama.loan;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.personal.chama.member.Member;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "loan")
@EntityListeners(AuditingEntityListener.class)
public class Loan {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "borrower_id")
	private Member borrower;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal principal;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Tenure tenure;

	/** Copied from the settings at disbursement and never changed afterwards. */
	@Column(name = "rate_percent", nullable = false, precision = 5, scale = 2)
	private BigDecimal ratePercent;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal interest;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal total;

	@Column(name = "disbursed_date", nullable = false)
	private LocalDate disbursedDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LoanStatus status = LoanStatus.ACTIVE;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private LoanKind kind;

	@Column(length = 300)
	private String purpose;

	@Column(length = 500)
	private String note;

	@Column(name = "disbursement_method", length = 50)
	private String disbursementMethod;

	@Column(name = "disbursement_reference", length = 100)
	private String disbursementReference;

	@Column(name = "cancel_reason", length = 500)
	private String cancelReason;

	@CreatedDate
	@Column(name = "recorded_at", nullable = false, updatable = false)
	private Instant recordedAt;

	@CreatedBy
	@Column(name = "recorded_by", nullable = false, updatable = false, length = 50)
	private String recordedBy;

	@Version
	private long version;

	@OneToMany(mappedBy = "loan", cascade = CascadeType.PERSIST)
	@OrderBy("number")
	private List<Installment> installments = new ArrayList<>();

	@OneToMany(mappedBy = "loan", cascade = CascadeType.PERSIST)
	private List<Guarantee> guarantees = new ArrayList<>();

	protected Loan() {
	}

	Loan(Member borrower, BigDecimal principal, Tenure tenure, BigDecimal ratePercent, LocalDate disbursedDate,
			LoanKind kind, String purpose, String note, String disbursementMethod, String disbursementReference) {
		this.borrower = borrower;
		this.principal = principal;
		this.tenure = tenure;
		this.ratePercent = ratePercent;
		this.interest = LoanCalculator.interest(principal, ratePercent);
		this.total = principal.add(interest);
		this.disbursedDate = disbursedDate;
		this.kind = kind;
		this.purpose = purpose;
		this.note = note;
		this.disbursementMethod = disbursementMethod;
		this.disbursementReference = disbursementReference;
	}

	void addInstallment(LoanCalculator.PlannedInstallment planned) {
		installments.add(new Installment(this, planned));
	}

	void addGuarantor(Member guarantor) {
		guarantees.add(new Guarantee(this, guarantor));
	}

	void markRepaid() {
		this.status = LoanStatus.REPAID;
	}

	void reopen() {
		this.status = LoanStatus.ACTIVE;
	}

	void cancel(String reason) {
		this.status = LoanStatus.CANCELLED;
		this.cancelReason = reason;
	}

	public boolean isActive() {
		return status == LoanStatus.ACTIVE;
	}

	public LocalDate endDate() {
		return installments.isEmpty() ? tenure.endDate(disbursedDate) : installments.get(installments.size() - 1).getDueDate();
	}

	public Long getId() {
		return id;
	}

	public Member getBorrower() {
		return borrower;
	}

	public BigDecimal getPrincipal() {
		return principal;
	}

	public Tenure getTenure() {
		return tenure;
	}

	public BigDecimal getRatePercent() {
		return ratePercent;
	}

	public BigDecimal getInterest() {
		return interest;
	}

	public BigDecimal getTotal() {
		return total;
	}

	public LocalDate getDisbursedDate() {
		return disbursedDate;
	}

	public LoanStatus getStatus() {
		return status;
	}

	public LoanKind getKind() {
		return kind;
	}

	public String getPurpose() {
		return purpose;
	}

	public String getNote() {
		return note;
	}

	public String getDisbursementMethod() {
		return disbursementMethod;
	}

	public String getDisbursementReference() {
		return disbursementReference;
	}

	public String getCancelReason() {
		return cancelReason;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}

	public String getRecordedBy() {
		return recordedBy;
	}

	public List<Installment> getInstallments() {
		return installments;
	}

	public List<Guarantee> getGuarantees() {
		return guarantees;
	}
}

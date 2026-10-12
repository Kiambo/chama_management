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

	/** When two missed installments in a row made the whole balance due. */
	@Column(name = "defaulted_on")
	private LocalDate defaultedOn;

	/** Recovery left a shortfall (a pool loss): waiting for the group to restructure or write off. */
	@Column(name = "awaiting_decision", nullable = false)
	private boolean awaitingDecision;

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
		this.awaitingDecision = false;
	}

	void reopen(LoanStatus status) {
		this.status = status;
	}

	void cancel(String reason) {
		this.status = LoanStatus.CANCELLED;
		this.cancelReason = reason;
	}

	/** Still being repaid: active, defaulted or restructured. */
	public boolean isActive() {
		return status == LoanStatus.ACTIVE || status == LoanStatus.DEFAULTED || status == LoanStatus.RESTRUCTURED;
	}

	void markDefaulted(LocalDate on) {
		this.status = LoanStatus.DEFAULTED;
		this.defaultedOn = on;
	}

	/** Undoes an automatic default, e.g. when a back-dated repayment shows a payment was on time. */
	void clearDefault(boolean restructured) {
		this.status = restructured ? LoanStatus.RESTRUCTURED : LoanStatus.ACTIVE;
		this.defaultedOn = null;
	}

	void clearPoolLoss() {
		this.awaitingDecision = false;
	}

	void flagPoolLoss() {
		this.awaitingDecision = true;
		if (status != LoanStatus.DEFAULTED) {
			this.status = LoanStatus.DEFAULTED;
		}
	}

	/** A longer tenure moves the loan to the tier for the new total tenure (brief 4.4). */
	void extend(Tenure newTenure, BigDecimal newRate) {
		this.tenure = newTenure;
		this.ratePercent = newRate;
		this.interest = LoanCalculator.interest(principal, newRate);
		this.total = principal.add(interest);
	}

	void restructure(BigDecimal extraCharge) {
		this.status = LoanStatus.RESTRUCTURED;
		this.awaitingDecision = false;
		this.defaultedOn = null;
		this.interest = interest.add(extraCharge);
		this.total = principal.add(interest);
	}

	/** Adds an agreed charge (e.g. unpaid late fees moved into a new plan) to what is repaid. */
	void addCharge(BigDecimal charge) {
		if (charge.signum() > 0) {
			this.interest = interest.add(charge);
			this.total = principal.add(interest);
		}
	}

	void writeOff() {
		this.status = LoanStatus.WRITTEN_OFF;
		this.awaitingDecision = false;
	}

	public LocalDate getDefaultedOn() {
		return defaultedOn;
	}

	public boolean isAwaitingDecision() {
		return awaitingDecision;
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

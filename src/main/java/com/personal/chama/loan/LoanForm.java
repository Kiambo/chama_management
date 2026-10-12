package com.personal.chama.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** A new loan, or an existing one being entered (decision L8). */
public class LoanForm {

	/** For loans "recorded as agreed": the standard schedule, or one payment at the end. */
	public enum Schedule {
		STANDARD, SINGLE_AT_END
	}

	@NotNull(message = "Choose the borrower")
	private Long borrowerId;

	@NotNull(message = "Enter the amount")
	@Positive(message = "The amount must be more than zero")
	@Digits(integer = 12, fraction = 0, message = "Use whole shillings")
	private BigDecimal amount;

	@NotNull(message = "Choose the tenure")
	private Tenure tenure;

	private List<Long> guarantorIds = new ArrayList<>();

	@Size(max = 300)
	private String purpose;

	@NotNull(message = "Enter the disbursement date")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate disbursedDate;

	@NotBlank(message = "Enter how it was paid out")
	@Size(max = 50)
	private String method;

	@Size(max = 100)
	private String reference;

	@NotNull
	private LoanKind kind = LoanKind.NEW;

	@NotNull
	private Schedule schedule = Schedule.STANDARD;

	@Size(max = 500)
	private String note;

	/** Chosen guarantors, ignoring empty pickers. */
	public List<Long> chosenGuarantors() {
		return guarantorIds == null ? List.of() : guarantorIds.stream().filter(Objects::nonNull).toList();
	}

	public Long getBorrowerId() {
		return borrowerId;
	}

	public void setBorrowerId(Long borrowerId) {
		this.borrowerId = borrowerId;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public Tenure getTenure() {
		return tenure;
	}

	public void setTenure(Tenure tenure) {
		this.tenure = tenure;
	}

	public List<Long> getGuarantorIds() {
		return guarantorIds;
	}

	public void setGuarantorIds(List<Long> guarantorIds) {
		this.guarantorIds = guarantorIds;
	}

	public String getPurpose() {
		return purpose;
	}

	public void setPurpose(String purpose) {
		this.purpose = purpose;
	}

	public LocalDate getDisbursedDate() {
		return disbursedDate;
	}

	public void setDisbursedDate(LocalDate disbursedDate) {
		this.disbursedDate = disbursedDate;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public String getReference() {
		return reference;
	}

	public void setReference(String reference) {
		this.reference = reference;
	}

	public LoanKind getKind() {
		return kind;
	}

	public void setKind(LoanKind kind) {
		this.kind = kind;
	}

	public Schedule getSchedule() {
		return schedule;
	}

	public void setSchedule(Schedule schedule) {
		this.schedule = schedule;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}
}

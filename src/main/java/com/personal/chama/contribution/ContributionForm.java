package com.personal.chama.contribution;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class ContributionForm {

	@NotNull(message = "Choose a member")
	private Long memberId;

	@NotNull(message = "Choose a month")
	@DateTimeFormat(pattern = "yyyy-MM")
	private YearMonth month;

	@NotNull(message = "Enter the amount")
	@Positive(message = "The amount must be more than zero")
	@Digits(integer = 12, fraction = 2, message = "Use at most 2 decimal places")
	private BigDecimal amount;

	@NotNull(message = "Enter the date it was paid")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate paidDate;

	@NotBlank(message = "Enter how it was paid")
	@Size(max = 50)
	private String method;

	@Size(max = 100)
	private String reference;

	private boolean agreedBackpay;

	@Size(max = 500)
	private String note;

	public Long getMemberId() {
		return memberId;
	}

	public void setMemberId(Long memberId) {
		this.memberId = memberId;
	}

	public YearMonth getMonth() {
		return month;
	}

	public void setMonth(YearMonth month) {
		this.month = month;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public LocalDate getPaidDate() {
		return paidDate;
	}

	public void setPaidDate(LocalDate paidDate) {
		this.paidDate = paidDate;
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

	public boolean isAgreedBackpay() {
		return agreedBackpay;
	}

	public void setAgreedBackpay(boolean agreedBackpay) {
		this.agreedBackpay = agreedBackpay;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}
}

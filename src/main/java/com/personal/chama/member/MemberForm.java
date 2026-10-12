package com.personal.chama.member;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class MemberForm {

	@NotBlank(message = "Enter the member's name")
	@Size(max = 100, message = "Name is too long")
	private String fullName;

	@NotNull(message = "Enter the date they joined")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate joinDate;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate mgrStartDate;

	public static MemberForm from(Member member) {
		MemberForm form = new MemberForm();
		form.fullName = member.getFullName();
		form.joinDate = member.getJoinDate();
		form.mgrStartDate = member.getMgrStartDate();
		return form;
	}

	@AssertTrue(message = "MGR start cannot be before the join date")
	public boolean isMgrStartDateValid() {
		return joinDate == null || mgrStartDate == null || !mgrStartDate.isBefore(joinDate);
	}

	public String getFullName() {
		return fullName;
	}

	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public LocalDate getJoinDate() {
		return joinDate;
	}

	public void setJoinDate(LocalDate joinDate) {
		this.joinDate = joinDate;
	}

	public LocalDate getMgrStartDate() {
		return mgrStartDate;
	}

	public void setMgrStartDate(LocalDate mgrStartDate) {
		this.mgrStartDate = mgrStartDate;
	}
}

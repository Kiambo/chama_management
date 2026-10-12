package com.personal.chama.contribution;

import java.time.YearMonth;
import java.util.List;

import com.personal.chama.member.Member;

/** Everything about one member's contributions for one month. */
public record MemberMonth(Member member, YearMonth month, Portions due, Portions paid, MonthStatus status,
		ContributionOptOut optOut, List<Contribution> payments) {

	public Portions owed() {
		return due.remainingAfter(paid);
	}

	public boolean canRecord() {
		return owed().total().signum() > 0;
	}

	public boolean optedOutSavings() {
		return optOut != null && optOut.isOptOutSavings();
	}

	public boolean optedOutMgr() {
		return optOut != null && optOut.isOptOutMgr();
	}
}

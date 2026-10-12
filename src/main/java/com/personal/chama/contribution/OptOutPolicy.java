package com.personal.chama.contribution;

import java.time.YearMonth;

import com.personal.chama.decision.DecisionInput;
import com.personal.chama.member.Member;

/**
 * A rule that can refuse a member's MGR opt-out, e.g. the merry-go-round's "not after
 * you've received this round" (decision M5). Throws to refuse.
 */
public interface OptOutPolicy {

	/** @param decision the group decision allowing an exception, or null */
	void checkMgrOptOut(Member member, YearMonth month, DecisionInput decision);
}

package com.personal.chama.contribution;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.personal.chama.ledger.LedgerService;
import com.personal.chama.member.MemberService;

/** A member's shares and month-by-month history. The polished version comes in Phase 6. */
@Controller
public class MemberLedgerController {

	private final MemberService memberService;
	private final ContributionService contributionService;
	private final LedgerService ledger;

	public MemberLedgerController(MemberService memberService, ContributionService contributionService,
			LedgerService ledger) {
		this.memberService = memberService;
		this.contributionService = contributionService;
		this.ledger = ledger;
	}

	@GetMapping("/members/{id}")
	public String show(@PathVariable Long id, Model model) {
		List<MemberMonth> history = contributionService.history(id);
		model.addAttribute("member", memberService.get(id));
		model.addAttribute("shares", ledger.sharesOf(id));
		model.addAttribute("history", history);
		model.addAttribute("paymentCount", history.stream().mapToInt(h -> h.payments().size()).sum());
		return "members/show";
	}
}

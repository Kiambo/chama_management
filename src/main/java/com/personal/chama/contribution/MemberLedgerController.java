package com.personal.chama.contribution;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.personal.chama.ledger.LedgerService;
import com.personal.chama.loan.LoanEvent;
import com.personal.chama.loan.LoanService;
import com.personal.chama.member.MemberService;

/** A member's shares and month-by-month history. The polished version comes in Phase 6. */
@Controller
public class MemberLedgerController {

	private final MemberService memberService;
	private final ContributionService contributionService;
	private final LedgerService ledger;
	private final LoanService loanService;

	public MemberLedgerController(MemberService memberService, ContributionService contributionService,
			LedgerService ledger, LoanService loanService) {
		this.memberService = memberService;
		this.contributionService = contributionService;
		this.ledger = ledger;
		this.loanService = loanService;
	}

	@GetMapping("/members/{id}")
	public String show(@PathVariable Long id, Model model) {
		List<MemberMonth> history = contributionService.history(id);
		model.addAttribute("member", memberService.get(id));
		model.addAttribute("shares", ledger.sharesOf(id));
		model.addAttribute("history", history);
		model.addAttribute("paymentCount", history.stream().mapToInt(h -> h.payments().size()).sum());
		List<LoanService.LoanView> loans = loanService.forBorrower(id);
		model.addAttribute("activeLoan", loans.stream().filter(v -> v.loan().isActive()).findFirst().orElse(null));
		model.addAttribute("loanHistories", loans.stream()
				.map(v -> new LoanHistoryCard(v, loanService.timeline(v))).toList());
		model.addAttribute("guaranteeing", loanService.guaranteedBy(id));
		model.addAttribute("debts", loanService.debtsInvolving(id));
		return "members/show";
	}

	/** A loan the member took, with everything that happened to it. */
	public record LoanHistoryCard(LoanService.LoanView view, List<LoanEvent> events) {
	}
}

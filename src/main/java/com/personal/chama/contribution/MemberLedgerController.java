package com.personal.chama.contribution;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.personal.chama.ledger.LedgerService;
import com.personal.chama.loan.LoanEvent;
import com.personal.chama.loan.LoanService;
import com.personal.chama.member.Member;
import com.personal.chama.member.MemberService;
import com.personal.chama.mgr.MgrService;

/** A member's shares and month-by-month history. The polished version comes in Phase 6. */
@Controller
public class MemberLedgerController {

	private final MemberService memberService;
	private final ContributionService contributionService;
	private final LedgerService ledger;
	private final LoanService loanService;
	private final MgrService mgr;

	public MemberLedgerController(MemberService memberService, ContributionService contributionService,
			LedgerService ledger, LoanService loanService, MgrService mgr) {
		this.memberService = memberService;
		this.contributionService = contributionService;
		this.ledger = ledger;
		this.loanService = loanService;
		this.mgr = mgr;
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
		model.addAttribute("payouts", mgr.payoutsTo(id));
		mgr.currentRound().ifPresent(round -> {
			model.addAttribute("round", round);
			round.slotOf(id).ifPresent(slot -> model.addAttribute("rotationSlot", slot));
		});
		return "members/show";
	}

	/** The member's statement as a CSV file (Phase 6 plan, Q7). */
	@GetMapping("/members/{id}/statement.csv")
	@Transactional(readOnly = true)
	public ResponseEntity<byte[]> statement(@PathVariable Long id) {
		Member member = memberService.get(id);
		LocalDate today = contributionService.today();
		String csv = MemberStatement.build(member, today, ledger.sharesOf(id), contributionService.history(id),
				loanService.forBorrower(id), loanService::timeline, mgr.payoutsTo(id), loanService.debtsInvolving(id));
		String file = "statement-" + member.getFullName().toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-" + today + ".csv";
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file).build().toString())
				.body(csv.getBytes(StandardCharsets.UTF_8));
	}

	/** A loan the member took, with everything that happened to it. */
	public record LoanHistoryCard(LoanService.LoanView view, List<LoanEvent> events) {
	}
}

package com.personal.chama.mgr;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.personal.chama.common.Money;
import com.personal.chama.config.ChamaProperties;
import com.personal.chama.contribution.ContributionException;
import com.personal.chama.decision.DecisionException;
import com.personal.chama.decision.DecisionInput;
import com.personal.chama.member.Member;
import com.personal.chama.member.MemberService;

@Controller
@RequestMapping("/mgr")
public class MgrController {

	static final List<String> PAYMENT_METHODS = List.of("M-Pesa", "PayPal", "Bank deposit", "Bank transfer", "Cash");

	private final MgrService mgr;
	private final MemberService memberService;
	private final YearMonth cutover;
	private final Clock clock;

	public MgrController(MgrService mgr, MemberService memberService, ChamaProperties properties, Clock clock) {
		this.mgr = mgr;
		this.memberService = memberService;
		this.cutover = properties.cutoverMonth();
		this.clock = clock;
	}

	@GetMapping
	public String index(Model model) {
		List<MgrService.RoundView> rounds = mgr.allRounds();
		List<MgrService.RoundView> newestFirst = new ArrayList<>(rounds);
		Collections.reverse(newestFirst);
		model.addAttribute("rounds", newestFirst);
		model.addAttribute("current", rounds.isEmpty() ? null : rounds.get(rounds.size() - 1));
		model.addAttribute("month", mgr.currentMonth());
		model.addAttribute("cutover", cutover);
		model.addAttribute("latestPayoutId", mgr.latestPayoutId());
		return "mgr/index";
	}

	// ---------------------------------------------------------------- rounds

	@GetMapping("/rounds/new")
	public String newRound(Model model) {
		Optional<MgrService.RoundView> last = mgr.currentRound();
		List<Member> eligible = eligibleMembers();
		List<Long> order = last.map(r -> r.round().getSlots().stream().map(s -> s.getMember())
				.filter(eligible::contains).map(Member::getId).toList())
				.orElse(eligible.stream().map(Member::getId).toList());
		// Anyone eligible who wasn't in the last round goes at the end.
		List<Long> full = new ArrayList<>(order);
		eligible.stream().map(Member::getId).filter(id -> !full.contains(id)).forEach(full::add);
		return roundForm(model, null, last.map(r -> r.round().getNumber() + 1).orElse(5),
				last.map(r -> r.lastMonth().plusMonths(1)).orElse(cutover.minusMonths(1)), full);
	}

	@GetMapping("/rounds/{id}/order")
	public String editRound(@PathVariable Long id, Model model) {
		MgrService.RoundView round = mgr.allRounds().stream().filter(r -> r.round().getId().equals(id)).findFirst()
				.orElseThrow(() -> new MgrException("No such round."));
		return roundForm(model, id, round.round().getNumber(), round.round().getStartMonth(),
				round.round().getSlots().stream().map(s -> s.getMember().getId()).toList());
	}

	@PostMapping("/rounds")
	public String saveRound(@RequestParam(required = false) Long roundId, @RequestParam int number,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth startMonth,
			@RequestParam(name = "order", required = false) List<Long> order, Model model, RedirectAttributes redirect) {
		try {
			mgr.saveRound(roundId, number, startMonth, order);
		} catch (MgrException e) {
			model.addAttribute("error", e.getMessage());
			return roundForm(model, roundId, number, startMonth, order == null ? List.of() : order);
		}
		redirect.addFlashAttribute("message", "Round " + number + " saved.");
		return "redirect:/mgr";
	}

	private String roundForm(Model model, Long roundId, int number, YearMonth startMonth, List<Long> order) {
		model.addAttribute("roundId", roundId);
		model.addAttribute("number", number);
		model.addAttribute("startMonth", startMonth);
		model.addAttribute("order", order);
		model.addAttribute("eligible", eligibleMembers());
		return "mgr/round-form";
	}

	private List<Member> eligibleMembers() {
		return memberService.listAll().stream().filter(m -> m.isActive() && m.getMgrStartDate() != null).toList();
	}

	// ---------------------------------------------------------------- payouts

	@GetMapping("/slots/{slotId}/payout")
	public String payoutForm(@PathVariable Long slotId, Model model, RedirectAttributes redirect) {
		try {
			model.addAttribute("p", mgr.preview(slotId));
		} catch (MgrException e) {
			redirect.addFlashAttribute("error", e.getMessage());
			return "redirect:/mgr";
		}
		model.addAttribute("methods", PAYMENT_METHODS);
		model.addAttribute("today", LocalDate.now(clock));
		model.addAttribute("cutover", cutover);
		return "mgr/payout";
	}

	@PostMapping("/slots/{slotId}/payout")
	public String payOut(@PathVariable Long slotId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidDate,
			@RequestParam(required = false) String method, @RequestParam(required = false) String reference,
			@RequestParam(defaultValue = "false") boolean deductSavings,
			@ModelAttribute("decision") DecisionInput decision, Model model, RedirectAttributes redirect) {
		try {
			MgrPayout payout = mgr.payOut(slotId, paidDate, method, reference, deductSavings, decision);
			redirect.addFlashAttribute("message", "Paid KES " + Money.format(payout.net()) + " to "
					+ payout.getMember().getFullName() + " for " + payout.getMonth() + ".");
			return "redirect:/mgr";
		} catch (MgrException | DecisionException | ContributionException e) {
			model.addAttribute("error", e.getMessage());
			model.addAttribute("method", method);
			return payoutForm(slotId, model, redirect);
		}
	}

	@PostMapping("/slots/{slotId}/history")
	public String history(@PathVariable Long slotId, @RequestParam(required = false) BigDecimal amount,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidDate,
			RedirectAttributes redirect) {
		try {
			mgr.recordHistory(slotId, amount, paidDate);
			redirect.addFlashAttribute("message", "Earlier payout recorded.");
		} catch (MgrException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/mgr";
	}

	@PostMapping("/payouts/{id}/correct")
	public String correct(@PathVariable Long id, @RequestParam(required = false) String reason,
			RedirectAttributes redirect) {
		try {
			if (reason == null || reason.isBlank()) {
				throw new MgrException("Say why the payout is being corrected.");
			}
			mgr.voidPayout(id, reason.strip());
			redirect.addFlashAttribute("message", "Payout reversed. The rotation is back to before it.");
		} catch (MgrException | ContributionException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/mgr";
	}
}

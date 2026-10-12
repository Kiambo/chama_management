package com.personal.chama.contribution;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.personal.chama.common.Money;
import com.personal.chama.config.ChamaProperties;
import com.personal.chama.decision.DecisionException;
import com.personal.chama.decision.DecisionInput;
import com.personal.chama.member.Member;
import com.personal.chama.member.MemberService;
import com.personal.chama.settings.PaymentMethodService;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/contributions")
public class ContributionController {

	/** Suggestions only; any other method can be typed in (Phase 2 plan, Q5). */
	private final PaymentMethodService paymentMethods;
	private final ContributionRules rules;
	private final ContributionService contributionService;
	private final MemberService memberService;
	private final ChamaProperties properties;

	public ContributionController(ContributionService contributionService, MemberService memberService,
			ChamaProperties properties, PaymentMethodService paymentMethods, ContributionRules rules) {
		this.contributionService = contributionService;
		this.paymentMethods = paymentMethods;
		this.rules = rules;
		this.memberService = memberService;
		this.properties = properties;
	}

	@GetMapping
	public String month(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
			Model model) {
		YearMonth shown = month != null ? month : contributionService.currentMonth();
		List<MemberMonth> rows = contributionService.monthOverview(shown);
		model.addAttribute("month", shown);
		model.addAttribute("rows", rows);
		model.addAttribute("months", monthChoices());
		model.addAttribute("dueDay", rules.dueDate(shown).getDayOfMonth());
		return "contributions/month";
	}

	@GetMapping("/new")
	public String newForm(@RequestParam(required = false) Long memberId,
			@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month, Model model) {
		ContributionForm form = new ContributionForm();
		form.setMemberId(memberId);
		form.setMonth(month != null ? month : contributionService.currentMonth());
		form.setPaidDate(contributionService.today());
		if (memberId != null) {
			form.setAmount(contributionService.memberMonth(memberId, form.getMonth()).owed().total());
		}
		return showForm(form, model);
	}

	@PostMapping
	public String record(@Valid @ModelAttribute("form") ContributionForm form, BindingResult result, Model model,
			RedirectAttributes redirect) {
		if (result.hasErrors()) {
			return showForm(form, model);
		}
		try {
			Contribution saved = contributionService.record(form);
			redirect.addFlashAttribute("message", "Recorded KES " + Money.format(saved.getAmount())
					+ " from " + saved.getMember().getFullName() + ".");
			return "redirect:/contributions?month=" + saved.getMonth();
		} catch (ContributionException e) {
			result.reject("rule", e.getMessage());
			return showForm(form, model);
		}
	}

	@GetMapping("/opt-out")
	public String optOutForm(@RequestParam Long memberId,
			@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month, Model model) {
		model.addAttribute("row", contributionService.memberMonth(memberId, month));
		return "contributions/opt-out";
	}

	@PostMapping("/opt-out")
	public String optOut(@RequestParam Long memberId, @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
			@RequestParam(defaultValue = "false") boolean savings, @RequestParam(defaultValue = "false") boolean mgr,
			@RequestParam(required = false) String note, @ModelAttribute("decision") DecisionInput decision, Model model,
			RedirectAttributes redirect) {
		try {
			boolean exception = decision != null && decision.getHowAgreed() != null && !decision.getHowAgreed().isBlank();
			contributionService.setOptOut(memberId, month, savings, mgr, note, exception ? decision : null);
			redirect.addFlashAttribute("message", "Opt-out saved.");
			return "redirect:/contributions?month=" + month;
		} catch (ContributionException | DecisionException e) {
			model.addAttribute("row", contributionService.memberMonth(memberId, month));
			model.addAttribute("error", e.getMessage());
			return "contributions/opt-out";
		}
	}

	@GetMapping("/{id}/correct")
	public String correctForm(@PathVariable Long id, Model model) {
		model.addAttribute("contribution", contributionService.get(id));
		return "contributions/correct";
	}

	@PostMapping("/{id}/correct")
	public String correct(@PathVariable Long id, @RequestParam(required = false) String reason, Model model,
			RedirectAttributes redirect) {
		Contribution contribution = contributionService.get(id);
		if (reason == null || reason.isBlank()) {
			model.addAttribute("contribution", contribution);
			model.addAttribute("error", "Say why it is being corrected.");
			return "contributions/correct";
		}
		try {
			contributionService.voidContribution(id, reason.strip());
		} catch (ContributionException e) {
			model.addAttribute("contribution", contribution);
			model.addAttribute("error", e.getMessage());
			return "contributions/correct";
		}
		redirect.addFlashAttribute("message", "Payment reversed. Record the correct one if needed.");
		return "redirect:/contributions?month=" + contribution.getMonth();
	}

	private String showForm(ContributionForm form, Model model) {
		model.addAttribute("form", form);
		model.addAttribute("members", memberService.listAll().stream().filter(Member::isActive).toList());
		model.addAttribute("months", monthChoices());
		model.addAttribute("methods", paymentMethods.activeNames());
		if (form.getMemberId() != null && form.getMonth() != null) {
			model.addAttribute("row", contributionService.memberMonth(form.getMemberId(), form.getMonth()));
		}
		return "contributions/form";
	}

	/** From the earliest join month (or six months before the cut-over) to three months ahead. */
	private List<YearMonth> monthChoices() {
		YearMonth earliest = properties.cutoverMonth().minusMonths(6);
		YearMonth latest = contributionService.currentMonth().plusMonths(3);
		List<YearMonth> months = new ArrayList<>();
		for (YearMonth m = latest; !m.isBefore(earliest); m = m.minusMonths(1)) {
			months.add(m);
		}
		return months;
	}
}

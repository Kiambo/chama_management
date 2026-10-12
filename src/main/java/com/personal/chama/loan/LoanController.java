package com.personal.chama.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.personal.chama.ledger.LedgerService;
import com.personal.chama.member.Member;
import com.personal.chama.member.MemberService;
import com.personal.chama.pool.OpeningBalanceService;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/loans")
public class LoanController {

	static final List<String> PAYMENT_METHODS = List.of("M-Pesa", "PayPal", "Bank deposit", "Bank transfer", "Cash");

	private final LoanService loanService;
	private final MemberService memberService;
	private final LedgerService ledger;
	private final LoanCalculator calculator;
	private final OpeningBalanceService openingBalances;

	public LoanController(LoanService loanService, MemberService memberService, LedgerService ledger,
			LoanCalculator calculator, OpeningBalanceService openingBalances) {
		this.loanService = loanService;
		this.memberService = memberService;
		this.ledger = ledger;
		this.calculator = calculator;
		this.openingBalances = openingBalances;
	}

	@GetMapping
	public String list(Model model) {
		List<LoanService.LoanView> all = loanService.all();
		model.addAttribute("active", all.stream().filter(v -> v.loan().isActive()).toList());
		model.addAttribute("closed", all.stream().filter(v -> !v.loan().isActive()).toList());
		model.addAttribute("outstanding", ledger.principalOutstanding());
		model.addAttribute("cash", ledger.mmfBalance());
		model.addAttribute("poolTotal", ledger.poolTotal());
		return "loans/list";
	}

	@GetMapping("/new")
	public String newLoan(@RequestParam(required = false) Long borrowerId,
			@RequestParam(defaultValue = "NEW") LoanKind kind, Model model) {
		LoanForm form = new LoanForm();
		form.setBorrowerId(borrowerId);
		form.setKind(kind);
		form.setDisbursedDate(loanService.today());
		return showForm(form, model);
	}

	/** "Review" shows a confirmation page; "Disburse" (from that page) saves the loan. */
	@PostMapping
	public String submit(@Valid @ModelAttribute("form") LoanForm form, BindingResult result,
			@RequestParam(defaultValue = "review") String action, Model model, RedirectAttributes redirect) {
		if (result.hasErrors()) {
			return showForm(form, model);
		}
		if ("review".equals(action)) {
			return review(form, model, result);
		}
		try {
			Loan loan = loanService.disburse(form);
			String message = "Loan of KES " + Money.format(loan.getPrincipal()) + " to "
					+ loan.getBorrower().getFullName() + " recorded.";
			if (openingBalances.isRecorded() && !loan.getDisbursedDate().isAfter(openingBalances.openingDate())) {
				message += " It was out on the opening date, so redo the opening balances to include it.";
			}
			redirect.addFlashAttribute("message", message);
			return "redirect:/loans/" + loan.getId();
		} catch (LoanException e) {
			result.reject("rule", e.getMessage());
			return showForm(form, model);
		}
	}

	/** "Back" from the confirmation page: the form again, as it was filled in. */
	@PostMapping("/edit")
	public String edit(@ModelAttribute("form") LoanForm form, Model model) {
		return showForm(form, model);
	}

	private String review(LoanForm form, Model model, BindingResult result) {
		// Run the same checks as disbursing, without saving, by previewing the terms and the assessment.
		LoanService.Assessment assessment = loanService.assess(form.getBorrowerId());
		if (form.getKind().checksRules()) {
			if (!assessment.eligible()) {
				result.reject("rule", assessment.blockers().get(0));
				return showForm(form, model);
			}
			if (form.getAmount().compareTo(assessment.limit().limit()) > 0) {
				result.reject("rule", "That is more than the borrowing limit of KES "
						+ Money.format(assessment.limit().limit()) + " (" + assessment.limit().bindingDescription() + ").");
				return showForm(form, model);
			}
			int required = calculator.requiredGuarantors(form.getAmount());
			if (form.chosenGuarantors().size() != required) {
				result.reject("rule", required == 0 ? "A loan of this size doesn't need a guarantor."
						: "Choose " + required + (required == 1 ? " guarantor." : " guarantors."));
				return showForm(form, model);
			}
		} else if (form.getNote() == null || form.getNote().isBlank()) {
			result.rejectValue("note", "required", "Say why this loan is recorded as agreed.");
			return showForm(form, model);
		}
		model.addAttribute("form", form);
		model.addAttribute("assessment", assessment);
		model.addAttribute("terms", loanService.terms(Money.orZero(form.getAmount()), form.getTenure(),
				form.getDisbursedDate(), form.getSchedule()));
		model.addAttribute("guarantors", form.chosenGuarantors().stream().map(memberService::get).toList());
		return "loans/confirm";
	}

	private String showForm(LoanForm form, Model model) {
		model.addAttribute("form", form);
		model.addAttribute("borrowers", memberService.listAll().stream().filter(Member::isActive).toList());
		model.addAttribute("tenures", Tenure.values());
		model.addAttribute("kinds", LoanKind.values());
		model.addAttribute("methods", PAYMENT_METHODS);
		model.addAttribute("rates", rates());
		model.addAttribute("settings", calculator.settings());
		if (form.getBorrowerId() != null) {
			model.addAttribute("assessment", loanService.assess(form.getBorrowerId()));
			model.addAttribute("guarantorOptions", loanService.guarantorOptions(form.getBorrowerId()));
		}
		return "loans/form";
	}

	private Map<String, BigDecimal> rates() {
		Map<String, BigDecimal> rates = new LinkedHashMap<>();
		for (Tenure t : Tenure.values()) {
			rates.put(t.name(), calculator.ratePercent(t));
		}
		return rates;
	}

	@GetMapping("/{id}")
	public String show(@PathVariable Long id, Model model) {
		model.addAttribute("v", loanService.view(id));
		model.addAttribute("methods", PAYMENT_METHODS);
		model.addAttribute("today", loanService.today());
		return "loans/show";
	}

	@PostMapping("/{id}/repayments")
	public String repay(@PathVariable Long id, @RequestParam(required = false) BigDecimal amount,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidDate,
			@RequestParam(required = false) String method, @RequestParam(required = false) String reference,
			Model model, RedirectAttributes redirect) {
		try {
			Repayment repayment = loanService.repay(id, amount, paidDate, method, reference);
			redirect.addFlashAttribute("message", "Repayment of KES " + Money.format(repayment.getAmount()) + " recorded.");
			return "redirect:/loans/" + id;
		} catch (LoanException e) {
			model.addAttribute("error", e.getMessage());
			model.addAttribute("v", loanService.view(id));
			model.addAttribute("methods", PAYMENT_METHODS);
			model.addAttribute("method", method);
			model.addAttribute("today", loanService.today());
			return "loans/show";
		}
	}

	@GetMapping("/{id}/repayments/{repaymentId}/correct")
	public String correctRepaymentForm(@PathVariable Long id, @PathVariable Long repaymentId, Model model) {
		model.addAttribute("v", loanService.view(id));
		model.addAttribute("repaymentId", repaymentId);
		return "loans/correct-repayment";
	}

	@PostMapping("/{id}/repayments/{repaymentId}/correct")
	public String correctRepayment(@PathVariable Long id, @PathVariable Long repaymentId,
			@RequestParam(required = false) String reason, Model model, RedirectAttributes redirect) {
		try {
			if (reason == null || reason.isBlank()) {
				throw new LoanException("Say why it is being corrected.");
			}
			loanService.voidRepayment(id, repaymentId, reason.strip());
		} catch (LoanException e) {
			model.addAttribute("error", e.getMessage());
			model.addAttribute("v", loanService.view(id));
			model.addAttribute("repaymentId", repaymentId);
			return "loans/correct-repayment";
		}
		redirect.addFlashAttribute("message", "Repayment reversed.");
		return "redirect:/loans/" + id;
	}

	@GetMapping("/{id}/cancel")
	public String cancelForm(@PathVariable Long id, Model model) {
		model.addAttribute("v", loanService.view(id));
		return "loans/cancel";
	}

	@PostMapping("/{id}/cancel")
	public String cancel(@PathVariable Long id, @RequestParam(required = false) String reason, Model model,
			RedirectAttributes redirect) {
		try {
			if (reason == null || reason.isBlank()) {
				throw new LoanException("Say why the loan is being cancelled.");
			}
			loanService.cancel(id, reason.strip());
		} catch (LoanException e) {
			model.addAttribute("error", e.getMessage());
			model.addAttribute("v", loanService.view(id));
			return "loans/cancel";
		}
		redirect.addFlashAttribute("message", "Loan cancelled and its disbursement reversed.");
		return "redirect:/loans/" + id;
	}

	@GetMapping("/{id}/agreement")
	public String agreement(@PathVariable Long id, Model model) {
		model.addAttribute("v", loanService.view(id));
		model.addAttribute("today", loanService.today());
		return "loans/agreement";
	}

	@PostMapping("/{id}/agreement/initials")
	public String initial(@PathVariable Long id, @RequestParam LoanSignature.Role role,
			@RequestParam(required = false) Long memberId, @RequestParam(required = false) String name,
			@RequestParam(required = false) String initials,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate signedDate,
			Model model, RedirectAttributes redirect) {
		try {
			loanService.recordInitials(id, role, memberId, name, initials, signedDate);
		} catch (LoanException e) {
			model.addAttribute("error", e.getMessage());
			model.addAttribute("v", loanService.view(id));
			model.addAttribute("today", loanService.today());
			return "loans/agreement";
		}
		redirect.addFlashAttribute("message", "Initials recorded.");
		return "redirect:/loans/" + id + "/agreement";
	}
}

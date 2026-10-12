package com.personal.chama.loan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

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
import com.personal.chama.decision.DecisionException;
import com.personal.chama.decision.DecisionInput;

/** Late payments and group decisions on a loan: recovery, waivers, extensions, new plans, write-offs. */
@Controller
@RequestMapping("/loans/{id}")
public class LoanActionsController {

	private final LoanService loanService;
	private final RecoveryService recovery;
	private final LoanDecisionService loanDecisions;
	private final LoanHistory history;

	public LoanActionsController(LoanService loanService, RecoveryService recovery, LoanDecisionService loanDecisions,
			LoanHistory history) {
		this.loanService = loanService;
		this.recovery = recovery;
		this.loanDecisions = loanDecisions;
		this.history = history;
	}

	// ---------------------------------------------------------------- recovery

	@GetMapping("/recover")
	public String recoverForm(@PathVariable Long id, Model model, RedirectAttributes redirect) {
		try {
			model.addAttribute("plan", recovery.plan(id));
		} catch (LoanException e) {
			redirect.addFlashAttribute("error", e.getMessage());
			return "redirect:/loans/" + id;
		}
		model.addAttribute("v", loanService.view(id));
		return "loans/recover";
	}

	@PostMapping("/recover")
	public String recover(@PathVariable Long id, RedirectAttributes redirect) {
		try {
			RecoveryService.Plan plan = recovery.recover(id);
			String message = "Recovered KES " + Money.format(plan.recovered()) + " from shares.";
			if (plan.poolLoss().signum() > 0) {
				message += " KES " + Money.format(plan.poolLoss())
						+ " could not be covered: a pool loss for the group to decide (restructure or write off).";
			}
			redirect.addFlashAttribute("message", message);
		} catch (LoanException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/loans/" + id;
	}

	// ---------------------------------------------------------------- fee waiver

	@GetMapping("/fees/{feeId}/waive")
	public String waiveForm(@PathVariable Long id, @PathVariable Long feeId, Model model) {
		LoanService.LoanView v = loanService.view(id);
		model.addAttribute("v", v);
		model.addAttribute("fee", v.fees().stream().filter(f -> f.getId().equals(feeId)).findFirst()
				.orElseThrow(() -> new LoanException("No such late fee on this loan.")));
		return "loans/waive";
	}

	@PostMapping("/fees/{feeId}/waive")
	public String waive(@PathVariable Long id, @PathVariable Long feeId, @ModelAttribute("decision") DecisionInput decision,
			Model model, RedirectAttributes redirect) {
		try {
			loanDecisions.waiveFee(id, feeId, decision);
		} catch (LoanException | DecisionException e) {
			model.addAttribute("error", e.getMessage());
			return waiveForm(id, feeId, model);
		}
		redirect.addFlashAttribute("message", "Late fee waived and the group decision recorded.");
		return "redirect:/loans/" + id;
	}

	// ---------------------------------------------------------------- extension

	@GetMapping("/extend")
	public String extendForm(@PathVariable Long id, Model model) {
		LoanService.LoanView v = loanService.view(id);
		model.addAttribute("v", v);
		model.addAttribute("tenures", Arrays.stream(Tenure.values())
				.filter(t -> t.ordinal() > v.loan().getTenure().ordinal()).toList());
		model.addAttribute("loanDecisions", loanDecisions);
		return "loans/extend";
	}

	@PostMapping("/extend")
	public String extend(@PathVariable Long id, @RequestParam(required = false) Tenure tenure,
			@ModelAttribute("decision") DecisionInput decision, Model model, RedirectAttributes redirect) {
		try {
			loanDecisions.extend(id, tenure, decision);
		} catch (LoanException | DecisionException e) {
			model.addAttribute("error", e.getMessage());
			return extendForm(id, model);
		}
		redirect.addFlashAttribute("message", "Loan extended. The new schedule is below.");
		return "redirect:/loans/" + id;
	}

	// ---------------------------------------------------------------- schedule change / restructure

	@GetMapping("/schedule")
	public String scheduleForm(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean restructure,
			Model model) {
		model.addAttribute("v", loanService.view(id));
		model.addAttribute("restructure", restructure);
		model.addAttribute("defaultFirstDue", history.today().plusMonths(1));
		return "loans/schedule";
	}

	@PostMapping("/schedule")
	public String changeSchedule(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean restructure,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate firstDue,
			@RequestParam(defaultValue = "1") int count, @RequestParam(required = false) BigDecimal extraCharge,
			@ModelAttribute("decision") DecisionInput decision, Model model, RedirectAttributes redirect) {
		try {
			loanDecisions.changeSchedule(id, firstDue, count, extraCharge, restructure, decision);
		} catch (LoanException | DecisionException e) {
			model.addAttribute("error", e.getMessage());
			return scheduleForm(id, restructure, model);
		}
		redirect.addFlashAttribute("message", restructure ? "Loan restructured." : "Schedule changed.");
		return "redirect:/loans/" + id;
	}

	// ---------------------------------------------------------------- write-off

	@GetMapping("/write-off")
	public String writeOffForm(@PathVariable Long id, Model model) {
		model.addAttribute("v", loanService.view(id));
		model.addAttribute("preview", loanDecisions.writeOffPreview(id));
		return "loans/write-off";
	}

	@PostMapping("/write-off")
	public String writeOff(@PathVariable Long id, @ModelAttribute("decision") DecisionInput decision, Model model,
			RedirectAttributes redirect) {
		try {
			loanDecisions.writeOff(id, decision);
		} catch (LoanException | DecisionException e) {
			model.addAttribute("error", e.getMessage());
			return writeOffForm(id, model);
		}
		redirect.addFlashAttribute("message", "Loan written off and the group decision recorded.");
		return "redirect:/loans/" + id;
	}

	// ---------------------------------------------------------------- repaying a guarantor

	@PostMapping("/debts/{debtId}/repay")
	public String repayGuarantor(@PathVariable Long id, @PathVariable Long debtId,
			@RequestParam(required = false) BigDecimal amount,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidDate,
			@RequestParam(required = false) String method, @RequestParam(required = false) String reference,
			RedirectAttributes redirect) {
		try {
			loanDecisions.repayGuarantor(debtId, amount, paidDate, method, reference);
			redirect.addFlashAttribute("message", "Repayment to the guarantor recorded; their shares are restored by that amount.");
		} catch (LoanException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/loans/" + id;
	}

	@PostMapping("/debts/{debtId}/payments/{paymentId}/correct")
	public String correctGuarantorRepayment(@PathVariable Long id, @PathVariable Long debtId,
			@PathVariable Long paymentId, @RequestParam(required = false) String reason, RedirectAttributes redirect) {
		try {
			if (reason == null || reason.isBlank()) {
				throw new LoanException("Say why it is being corrected.");
			}
			loanDecisions.voidGuarantorRepayment(debtId, paymentId, reason.strip());
			redirect.addFlashAttribute("message", "Repayment to the guarantor reversed.");
		} catch (LoanException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/loans/" + id;
	}
}

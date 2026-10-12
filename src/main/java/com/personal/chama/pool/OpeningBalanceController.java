package com.personal.chama.pool;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.personal.chama.member.Member;
import com.personal.chama.member.MemberService;

@Controller
@RequestMapping("/opening-balances")
public class OpeningBalanceController {

	private final OpeningBalanceService service;
	private final MemberService memberService;

	public OpeningBalanceController(OpeningBalanceService service, MemberService memberService) {
		this.service = service;
		this.memberService = memberService;
	}

	@GetMapping
	public String show(Model model) {
		return render(model, Map.of());
	}

	/**
	 * Form fields: statementBalance, and shares_{memberId} for each member. With
	 * confirm=false the page only shows the worked-out group money for checking.
	 */
	@PostMapping
	public String record(@RequestParam Map<String, String> params,
			@RequestParam(defaultValue = "false") boolean confirm, Model model, RedirectAttributes redirect) {
		Map<Long, BigDecimal> shares = new LinkedHashMap<>();
		BigDecimal statement;
		try {
			statement = parse(params.get("statementBalance"));
			for (Member m : members()) {
				shares.put(m.getId(), parse(params.get("shares_" + m.getId())));
			}
		} catch (NumberFormatException e) {
			model.addAttribute("error", "Amounts must be numbers, e.g. 34000.");
			return render(model, params);
		}
		if (statement == null) {
			model.addAttribute("error", "Enter the statement balance.");
			return render(model, params);
		}
		BigDecimal total = shares.values().stream().filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal groupMoney = service.groupMoney(statement, total);
		if (!confirm) {
			model.addAttribute("preview", new Preview(statement, total, service.loansOutAtOpening(), groupMoney));
			return render(model, params);
		}
		try {
			service.record(shares, statement);
		} catch (PoolException e) {
			model.addAttribute("error", e.getMessage());
			return render(model, params);
		}
		redirect.addFlashAttribute("message", "Opening balances recorded.");
		return "redirect:/opening-balances";
	}

	@PostMapping("/redo")
	public String redo(@RequestParam(required = false) String reason, Model model, RedirectAttributes redirect) {
		if (reason == null || reason.isBlank()) {
			model.addAttribute("error", "Say why the opening balances are being redone.");
			return render(model, Map.of());
		}
		service.redo(reason.strip());
		redirect.addFlashAttribute("message", "Opening balances reversed. Enter them again below.");
		return "redirect:/opening-balances";
	}

	private String render(Model model, Map<String, String> params) {
		model.addAttribute("recorded", service.isRecorded());
		model.addAttribute("entries", service.current());
		model.addAttribute("members", members());
		model.addAttribute("openingDate", service.openingDate());
		model.addAttribute("values", params);
		return "pool/opening-balances";
	}

	private List<Member> members() {
		return memberService.listAll().stream().filter(Member::isActive).toList();
	}

	private static BigDecimal parse(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return new BigDecimal(value.replace(",", "").strip());
	}

	public record Preview(BigDecimal statement, BigDecimal totalShares, BigDecimal loansOut, BigDecimal groupMoney) {
	}
}

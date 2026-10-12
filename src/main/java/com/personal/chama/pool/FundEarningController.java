package com.personal.chama.pool;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.personal.chama.common.Money;

@Controller
@RequestMapping("/fund-earnings")
public class FundEarningController {

	private final FundEarningService service;
	private final Clock clock;

	public FundEarningController(FundEarningService service, Clock clock) {
		this.service = service;
		this.clock = clock;
	}

	@GetMapping
	public String list(Model model) {
		model.addAttribute("earnings", service.list());
		model.addAttribute("defaultMonth", YearMonth.now(clock).minusMonths(1));
		return "pool/fund-earnings";
	}

	@PostMapping
	public String record(@RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
			@RequestParam(required = false) BigDecimal amount, @RequestParam(required = false) String reference,
			@RequestParam(required = false) MultipartFile statement, Model model, RedirectAttributes redirect) {
		String error = null;
		if (month == null) {
			error = "Choose the month.";
		} else if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
			error = "Enter the amount earned, more than zero, with at most 2 decimal places.";
		}
		if (error == null) {
			try {
				FundEarning saved = service.record(month, Money.orZero(amount), reference, statement);
				redirect.addFlashAttribute("message", "Recorded KES " + Money.format(saved.getAmount()) + " fund earnings.");
				return "redirect:/fund-earnings";
			} catch (PoolException e) {
				error = e.getMessage();
			}
		}
		model.addAttribute("error", error);
		model.addAttribute("earnings", service.list());
		model.addAttribute("defaultMonth", month != null ? month : YearMonth.now(clock).minusMonths(1));
		model.addAttribute("amount", amount);
		model.addAttribute("reference", reference);
		return "pool/fund-earnings";
	}

	@GetMapping("/{id}/statement")
	@Transactional(readOnly = true)
	public ResponseEntity<byte[]> statement(@PathVariable Long id) {
		FundEarning earning = service.get(id);
		if (!earning.hasStatement()) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(earning.getStatementContentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.inline().filename(earning.getStatementFileName()).build().toString())
				.body(earning.getStatementFile());
	}

	@GetMapping("/{id}/correct")
	public String correctForm(@PathVariable Long id, Model model) {
		model.addAttribute("earning", service.get(id));
		return "pool/correct-earning";
	}

	@PostMapping("/{id}/correct")
	public String correct(@PathVariable Long id, @RequestParam(required = false) String reason, Model model,
			RedirectAttributes redirect) {
		try {
			if (reason == null || reason.isBlank()) {
				throw new PoolException("Say why it is being corrected.");
			}
			service.voidEarning(id, reason.strip());
		} catch (PoolException e) {
			model.addAttribute("earning", service.get(id));
			model.addAttribute("error", e.getMessage());
			return "pool/correct-earning";
		}
		redirect.addFlashAttribute("message", "Fund earnings reversed.");
		return "redirect:/fund-earnings";
	}
}

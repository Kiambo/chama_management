package com.personal.chama.settings;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

import com.personal.chama.config.ChamaProperties;
import com.personal.chama.decision.DecisionException;
import com.personal.chama.decision.DecisionInput;

@Controller
@RequestMapping("/settings")
public class SettingsController {

	static final Map<String, String> GROUPS = new LinkedHashMap<>();
	static {
		GROUPS.put("contributions", "Monthly contribution");
		GROUPS.put("rates", "Loan interest (flat, charged once)");
		GROUPS.put("limits", "Borrowing limits");
		GROUPS.put("guarantors", "Guarantors");
		GROUPS.put("late", "Late payments");
		GROUPS.put("goal", "Goal");
	}

	private final SettingsService settings;
	private final PaymentMethodService paymentMethods;
	private final Clock clock;

	public SettingsController(SettingsService settings, PaymentMethodService paymentMethods, Clock clock) {
		this.settings = settings;
		this.paymentMethods = paymentMethods;
		this.clock = clock;
	}

	@GetMapping
	public String show(Model model) {
		LocalDate today = LocalDate.now(clock);
		ChamaProperties current = settings.current();
		Map<String, List<SettingKey>> byGroup = new LinkedHashMap<>();
		GROUPS.keySet().forEach(g -> byGroup.put(g, Arrays.stream(SettingKey.values())
				.filter(k -> k.getGroup().equals(g)).toList()));
		Map<String, String> values = new LinkedHashMap<>();
		for (SettingKey k : SettingKey.values()) {
			values.put(k.name(), SettingsService.plain(k.read(current)));
		}
		model.addAttribute("groups", GROUPS);
		model.addAttribute("byGroup", byGroup);
		model.addAttribute("values", values);
		model.addAttribute("history", settings.history());
		model.addAttribute("today", today);
		model.addAttribute("nextMonth", YearMonth.from(today).plusMonths(1).atDay(1));
		model.addAttribute("methods", paymentMethods.all());
		return "settings/show";
	}

	@PostMapping
	public String change(@RequestParam Map<String, String> params,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveFrom,
			@ModelAttribute("decision") DecisionInput decision, RedirectAttributes redirect) {
		Map<SettingKey, BigDecimal> requested = new EnumMap<>(SettingKey.class);
		try {
			for (SettingKey k : SettingKey.values()) {
				String raw = params.get("v_" + k.name());
				if (raw != null && !raw.isBlank()) {
					requested.put(k, new BigDecimal(raw.replace(",", "").strip()));
				}
			}
			List<SettingKey> changed = settings.change(requested, effectiveFrom, decision);
			redirect.addFlashAttribute("message", changed.size() + (changed.size() == 1 ? " setting" : " settings")
					+ " changed from " + effectiveFrom + ", with the group decision recorded.");
		} catch (NumberFormatException e) {
			redirect.addFlashAttribute("error", "Values must be numbers.");
		} catch (SettingsException | DecisionException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/settings";
	}

	@PostMapping("/methods")
	public String addMethod(@RequestParam(required = false) String name, RedirectAttributes redirect) {
		try {
			paymentMethods.add(name);
			redirect.addFlashAttribute("message", "Payment method added.");
		} catch (SettingsException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/settings#methods";
	}

	@PostMapping("/methods/{id}")
	public String setMethodActive(@PathVariable Long id, @RequestParam boolean active, RedirectAttributes redirect) {
		try {
			paymentMethods.setActive(id, active);
			redirect.addFlashAttribute("message", active ? "Payment method shown again." : "Payment method hidden.");
		} catch (SettingsException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/settings#methods";
	}
}

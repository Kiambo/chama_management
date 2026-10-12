package com.personal.chama.settings;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The payment methods offered in every payment form. "Other..." is always offered too,
 * so an unusual method can still be typed in. A method is hidden, never deleted, because
 * past payments refer to it.
 */
@Service
@Transactional(readOnly = true)
public class PaymentMethodService {

	private final PaymentMethodRepository methods;

	public PaymentMethodService(PaymentMethodRepository methods) {
		this.methods = methods;
	}

	/** The names shown in the dropdowns, in order. */
	public List<String> activeNames() {
		return methods.findAllByOrderByPositionAscIdAsc().stream().filter(PaymentMethod::isActive)
				.map(PaymentMethod::getName).toList();
	}

	public List<PaymentMethod> all() {
		return methods.findAllByOrderByPositionAscIdAsc();
	}

	@Transactional
	public void add(String name) {
		String n = name == null ? "" : name.strip();
		if (n.isEmpty() || n.length() > 50) {
			throw new SettingsException("Enter a method name of up to 50 characters.");
		}
		if (n.equalsIgnoreCase("Other") || n.startsWith("__")) {
			throw new SettingsException("\"Other…\" is always offered already.");
		}
		if (methods.existsByNameIgnoreCase(n)) {
			throw new SettingsException(n + " is already in the list.");
		}
		int last = methods.findAllByOrderByPositionAscIdAsc().stream().mapToInt(PaymentMethod::getPosition).max().orElse(0);
		methods.save(new PaymentMethod(n, last + 1));
	}

	@Transactional
	public void setActive(Long id, boolean active) {
		PaymentMethod method = methods.findById(id).orElseThrow(() -> new SettingsException("No such method."));
		if (!active && activeNames().size() <= 1) {
			throw new SettingsException("Keep at least one method in the list.");
		}
		method.setActive(active);
	}
}

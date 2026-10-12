package com.personal.chama.settings;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.chama.config.ChamaProperties;
import com.personal.chama.decision.DecisionInput;
import com.personal.chama.decision.DecisionService;
import com.personal.chama.decision.GroupDecision;

/**
 * The business settings in force on any date: the defaults from application.properties,
 * then every change the group has decided, each from its effective date. Code that needs
 * a setting asks for it "as at" the date that matters, so a change never rewrites the
 * past: a month's contribution uses the amounts for that month, a missed payment the grace
 * days in force when it was due, and loans already out keep their own stored rate.
 */
@Service
public class SettingsService {

	private final ChamaProperties defaults;
	private final SettingValueRepository values;
	private final DecisionService decisions;
	private final Clock clock;
	private volatile List<SettingValue> cache;

	public SettingsService(ChamaProperties defaults, SettingValueRepository values, DecisionService decisions,
			Clock clock) {
		this.defaults = defaults;
		this.values = values;
		this.decisions = decisions;
		this.clock = clock;
	}

	public ChamaProperties current() {
		return asOf(LocalDate.now(clock));
	}

	/** The settings in force on this date. */
	public ChamaProperties asOf(LocalDate date) {
		ChamaProperties properties = defaults;
		Map<SettingKey, BigDecimal> latest = new EnumMap<>(SettingKey.class);
		for (SettingValue v : all()) {
			if (!v.getEffectiveFrom().isAfter(date)) {
				latest.put(v.getKey(), v.getValue());
			}
		}
		for (Map.Entry<SettingKey, BigDecimal> e : latest.entrySet()) {
			properties = e.getKey().write(properties, e.getValue());
		}
		return properties;
	}

	public ChamaProperties defaults() {
		return defaults;
	}

	private List<SettingValue> all() {
		List<SettingValue> loaded = cache;
		if (loaded == null) {
			loaded = values.findAllByOrderByEffectiveFromAscIdAsc();
			cache = loaded;
		}
		return loaded;
	}

	/** Every change ever decided, newest first. */
	public List<SettingValue> history() {
		List<SettingValue> list = new ArrayList<>(values.findAllByOrderByEffectiveFromAscIdAsc());
		java.util.Collections.reverse(list);
		return list;
	}

	/**
	 * Saves the values that differ from those in force on the effective date, with the
	 * group decision behind them. Contribution settings change from the 1st of a month
	 * that hasn't started yet; the others from today or later.
	 */
	@Transactional
	public List<SettingKey> change(Map<SettingKey, BigDecimal> requested, LocalDate effectiveFrom, DecisionInput input) {
		LocalDate today = LocalDate.now(clock);
		if (effectiveFrom == null || effectiveFrom.isBefore(today)) {
			throw new SettingsException("Choose when the change starts: today or later. Changes never apply to the past.");
		}
		ChamaProperties before = asOf(effectiveFrom);
		Map<SettingKey, BigDecimal> changed = new EnumMap<>(SettingKey.class);
		for (Map.Entry<SettingKey, BigDecimal> e : requested.entrySet()) {
			if (e.getValue() != null && e.getKey().read(before).compareTo(e.getValue()) != 0) {
				changed.put(e.getKey(), e.getValue());
			}
		}
		if (changed.isEmpty()) {
			throw new SettingsException("Nothing was changed.");
		}
		boolean monthly = changed.keySet().stream().anyMatch(SettingKey::isMonthly);
		if (monthly && (effectiveFrom.getDayOfMonth() != 1 || !effectiveFrom.isAfter(today))) {
			throw new SettingsException("Contribution changes start on the 1st of a month that hasn't begun yet,"
					+ " so a month in progress keeps its amounts.");
		}
		ChamaProperties after = before;
		for (Map.Entry<SettingKey, BigDecimal> e : changed.entrySet()) {
			validate(e.getKey(), e.getValue());
			after = e.getKey().write(after, e.getValue());
		}
		if (after.loan().twoGuarantorsAbove().compareTo(after.loan().oneGuarantorAbove()) <= 0) {
			throw new SettingsException("The amount needing 2 guarantors must be above the amount needing 1.");
		}
		ChamaProperties finalBefore = before;
		String summary = "Settings from " + effectiveFrom + ": " + changed.entrySet().stream()
				.map(e -> e.getKey().getLabel() + " " + plain(e.getKey().read(finalBefore)) + " -> " + plain(e.getValue()))
				.collect(Collectors.joining("; "));
		GroupDecision decision = decisions.record(GroupDecision.Kind.SETTING_CHANGE,
				summary.length() > 300 ? summary.substring(0, 297) + "..." : summary, input, null);
		for (Map.Entry<SettingKey, BigDecimal> e : changed.entrySet()) {
			values.save(new SettingValue(e.getKey(), e.getValue(), effectiveFrom, decision.getId()));
		}
		cache = null;
		return List.copyOf(changed.keySet());
	}

	private static void validate(SettingKey key, BigDecimal value) {
		switch (key.getUnit()) {
			case PERCENT -> require(value.signum() >= 0 && value.compareTo(BigDecimal.valueOf(100)) <= 0,
					key.getLabel() + " must be between 0 and 100.");
			case DAY -> require(value.stripTrailingZeros().scale() <= 0 && value.intValue() >= 1 && value.intValue() <= 28,
					key.getLabel() + " must be a whole day between 1 and 28.");
			case DAYS -> require(value.stripTrailingZeros().scale() <= 0 && value.intValue() >= 0 && value.intValue() <= 60,
					key.getLabel() + " must be a whole number of days, 0 to 60.");
			case TIMES -> require(value.signum() > 0 && value.compareTo(BigDecimal.valueOf(20)) <= 0,
					key.getLabel() + " must be more than 0 and at most 20.");
			case KES -> require(value.signum() >= 0 && value.stripTrailingZeros().scale() <= 0,
					key.getLabel() + " must be whole shillings, not negative.");
		}
	}

	private static void require(boolean ok, String message) {
		if (!ok) {
			throw new SettingsException(message);
		}
	}

	public static String plain(BigDecimal value) {
		return value.stripTrailingZeros().toPlainString();
	}
}

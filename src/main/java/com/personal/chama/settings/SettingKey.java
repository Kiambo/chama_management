package com.personal.chama.settings;

import java.math.BigDecimal;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.personal.chama.config.ChamaProperties;
import com.personal.chama.config.ChamaProperties.Contribution;
import com.personal.chama.config.ChamaProperties.Loan;
import com.personal.chama.config.ChamaProperties.Rates;

/**
 * The settings that can be changed in the app, each with how to read it from the settings
 * and how to put a new value in. The cut-over month is not here: it never changes.
 */
public enum SettingKey {

	MMF_AMOUNT("contributions", "Savings (MMF) part of the monthly contribution", Unit.KES,
			p -> p.contribution().mmfAmount(),
			(p, v) -> with(p, new Contribution(v, p.contribution().mgrAmount(), p.contribution().dueDay()))),
	MGR_AMOUNT("contributions", "Merry-go-round part of the monthly contribution", Unit.KES,
			p -> p.contribution().mgrAmount(),
			(p, v) -> with(p, new Contribution(p.contribution().mmfAmount(), v, p.contribution().dueDay()))),
	DUE_DAY("contributions", "Due day of the month (contributions; latest MGR payout)", Unit.DAY,
			p -> BigDecimal.valueOf(p.contribution().dueDay()),
			(p, v) -> with(p, new Contribution(p.contribution().mmfAmount(), p.contribution().mgrAmount(), v.intValue()))),

	RATE_TWO_WEEKS("rates", "Interest: up to 2 weeks", Unit.PERCENT, p -> p.loan().rates().twoWeeks(),
			(p, v) -> withRates(p, new Rates(v, r(p).oneMonth(), r(p).upToThreeMonths(), r(p).upToSixMonths()))),
	RATE_ONE_MONTH("rates", "Interest: up to 1 month", Unit.PERCENT, p -> p.loan().rates().oneMonth(),
			(p, v) -> withRates(p, new Rates(r(p).twoWeeks(), v, r(p).upToThreeMonths(), r(p).upToSixMonths()))),
	RATE_UP_TO_THREE_MONTHS("rates", "Interest: 2 to 3 months", Unit.PERCENT, p -> p.loan().rates().upToThreeMonths(),
			(p, v) -> withRates(p, new Rates(r(p).twoWeeks(), r(p).oneMonth(), v, r(p).upToSixMonths()))),
	RATE_UP_TO_SIX_MONTHS("rates", "Interest: 4 to 6 months", Unit.PERCENT, p -> p.loan().rates().upToSixMonths(),
			(p, v) -> withRates(p, new Rates(r(p).twoWeeks(), r(p).oneMonth(), r(p).upToThreeMonths(), v))),

	SHARES_MULTIPLE("limits", "Borrowing limit: times the member's shares", Unit.TIMES, p -> p.loan().sharesMultiple(),
			(p, v) -> withLoan(p, l -> new Loan(v, l.perLoanCapPercent(), l.totalCapPercent(), l.guarantorMinShares(),
					l.oneGuarantorAbove(), l.twoGuarantorsAbove(), l.dueSoonDays(), l.rates(), l.graceDays(),
					l.lateFeeMinimum(), l.lateFeePercent()))),
	PER_LOAN_CAP("limits", "One loan at most: % of the pool", Unit.PERCENT, p -> p.loan().perLoanCapPercent(),
			(p, v) -> withLoan(p, l -> new Loan(l.sharesMultiple(), v, l.totalCapPercent(), l.guarantorMinShares(),
					l.oneGuarantorAbove(), l.twoGuarantorsAbove(), l.dueSoonDays(), l.rates(), l.graceDays(),
					l.lateFeeMinimum(), l.lateFeePercent()))),
	TOTAL_CAP("limits", "All loans together at most: % of the pool", Unit.PERCENT, p -> p.loan().totalCapPercent(),
			(p, v) -> withLoan(p, l -> new Loan(l.sharesMultiple(), l.perLoanCapPercent(), v, l.guarantorMinShares(),
					l.oneGuarantorAbove(), l.twoGuarantorsAbove(), l.dueSoonDays(), l.rates(), l.graceDays(),
					l.lateFeeMinimum(), l.lateFeePercent()))),

	GUARANTOR_MIN_SHARES("guarantors", "A guarantor needs at least these shares", Unit.KES,
			p -> p.loan().guarantorMinShares(),
			(p, v) -> withLoan(p, l -> new Loan(l.sharesMultiple(), l.perLoanCapPercent(), l.totalCapPercent(), v,
					l.oneGuarantorAbove(), l.twoGuarantorsAbove(), l.dueSoonDays(), l.rates(), l.graceDays(),
					l.lateFeeMinimum(), l.lateFeePercent()))),
	ONE_GUARANTOR_ABOVE("guarantors", "Loans above this need 1 guarantor", Unit.KES, p -> p.loan().oneGuarantorAbove(),
			(p, v) -> withLoan(p, l -> new Loan(l.sharesMultiple(), l.perLoanCapPercent(), l.totalCapPercent(),
					l.guarantorMinShares(), v, l.twoGuarantorsAbove(), l.dueSoonDays(), l.rates(), l.graceDays(),
					l.lateFeeMinimum(), l.lateFeePercent()))),
	TWO_GUARANTORS_ABOVE("guarantors", "Loans above this need 2 guarantors", Unit.KES,
			p -> p.loan().twoGuarantorsAbove(),
			(p, v) -> withLoan(p, l -> new Loan(l.sharesMultiple(), l.perLoanCapPercent(), l.totalCapPercent(),
					l.guarantorMinShares(), l.oneGuarantorAbove(), v, l.dueSoonDays(), l.rates(), l.graceDays(),
					l.lateFeeMinimum(), l.lateFeePercent()))),

	GRACE_DAYS("late", "Grace days after a loan payment's due date", Unit.DAYS, p -> BigDecimal.valueOf(p.loan().graceDays()),
			(p, v) -> withLoan(p, l -> new Loan(l.sharesMultiple(), l.perLoanCapPercent(), l.totalCapPercent(),
					l.guarantorMinShares(), l.oneGuarantorAbove(), l.twoGuarantorsAbove(), l.dueSoonDays(), l.rates(),
					v.intValue(), l.lateFeeMinimum(), l.lateFeePercent()))),
	LATE_FEE_MINIMUM("late", "Late fee: at least", Unit.KES, p -> p.loan().lateFeeMinimum(),
			(p, v) -> withLoan(p, l -> new Loan(l.sharesMultiple(), l.perLoanCapPercent(), l.totalCapPercent(),
					l.guarantorMinShares(), l.oneGuarantorAbove(), l.twoGuarantorsAbove(), l.dueSoonDays(), l.rates(),
					l.graceDays(), v, l.lateFeePercent()))),
	LATE_FEE_PERCENT("late", "Late fee: % of the missed installment, if more", Unit.PERCENT,
			p -> p.loan().lateFeePercent(),
			(p, v) -> withLoan(p, l -> new Loan(l.sharesMultiple(), l.perLoanCapPercent(), l.totalCapPercent(),
					l.guarantorMinShares(), l.oneGuarantorAbove(), l.twoGuarantorsAbove(), l.dueSoonDays(), l.rates(),
					l.graceDays(), l.lateFeeMinimum(), v))),

	GOAL_AMOUNT("goal", "Savings goal shown on the dashboard", Unit.KES, ChamaProperties::goalAmount,
			(p, v) -> new ChamaProperties(p.contribution(), p.cutoverMonth(), v, p.loan()));

	public enum Unit {
		KES, PERCENT, TIMES, DAY, DAYS
	}

	private final String group;
	private final String label;
	private final Unit unit;
	private final Function<ChamaProperties, BigDecimal> read;
	private final BiFunction<ChamaProperties, BigDecimal, ChamaProperties> write;

	SettingKey(String group, String label, Unit unit, Function<ChamaProperties, BigDecimal> read,
			BiFunction<ChamaProperties, BigDecimal, ChamaProperties> write) {
		this.group = group;
		this.label = label;
		this.unit = unit;
		this.read = read;
		this.write = write;
	}

	public String getGroup() {
		return group;
	}

	public String getLabel() {
		return label;
	}

	public Unit getUnit() {
		return unit;
	}

	public BigDecimal read(ChamaProperties properties) {
		return read.apply(properties);
	}

	ChamaProperties write(ChamaProperties properties, BigDecimal value) {
		return write.apply(properties, value);
	}

	/** Contribution settings apply to whole months, so they change from the 1st of a month. */
	public boolean isMonthly() {
		return group.equals("contributions");
	}

	private static Rates r(ChamaProperties p) {
		return p.loan().rates();
	}

	private static ChamaProperties with(ChamaProperties p, Contribution c) {
		return new ChamaProperties(c, p.cutoverMonth(), p.goalAmount(), p.loan());
	}

	private static ChamaProperties withRates(ChamaProperties p, Rates rates) {
		return withLoan(p, l -> new Loan(l.sharesMultiple(), l.perLoanCapPercent(), l.totalCapPercent(),
				l.guarantorMinShares(), l.oneGuarantorAbove(), l.twoGuarantorsAbove(), l.dueSoonDays(), rates,
				l.graceDays(), l.lateFeeMinimum(), l.lateFeePercent()));
	}

	private static ChamaProperties withLoan(ChamaProperties p, Function<Loan, Loan> change) {
		return new ChamaProperties(p.contribution(), p.cutoverMonth(), p.goalAmount(), change.apply(p.loan()));
	}
}

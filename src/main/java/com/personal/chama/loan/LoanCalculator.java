package com.personal.chama.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.personal.chama.common.Money;
import com.personal.chama.config.ChamaProperties;
import com.personal.chama.settings.SettingsService;

/**
 * Loan arithmetic (brief 4.4): tiered flat interest, installments, borrowing limits and
 * how many guarantors are needed. Pure logic, so every rule is unit-tested.
 */
@Component
public class LoanCalculator {

	private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

	private final Supplier<ChamaProperties.Loan> current;

	/** Fixed settings, for tests. */
	public LoanCalculator(ChamaProperties properties) {
		this.current = properties::loan;
	}

	@Autowired
	public LoanCalculator(SettingsService settings) {
		this.current = () -> settings.current().loan();
	}

	public BigDecimal ratePercent(Tenure tenure) {
		return tenure.ratePercent(current.get().rates());
	}

	/** Interest: once, on the original principal, rounded to whole KES (half up). */
	public static BigDecimal interest(BigDecimal principal, BigDecimal ratePercent) {
		return wholeShillings(principal.multiply(ratePercent).divide(HUNDRED, 10, RoundingMode.HALF_UP));
	}

	/**
	 * The repayment plan. Each installment is (principal + interest) / n rounded to whole
	 * KES; any remainder goes on the last one so the total is exact. Interest is spread
	 * the same way, so each installment says how much of it is interest.
	 */
	public static List<PlannedInstallment> schedule(BigDecimal principal, BigDecimal ratePercent, Tenure tenure,
			LocalDate disbursed) {
		return split(principal, interest(principal, ratePercent), tenure.dueDates(disbursed));
	}

	/** One payment of the whole total on the end date, for loans "recorded as agreed". */
	public static List<PlannedInstallment> singlePaymentAtEnd(BigDecimal principal, BigDecimal ratePercent,
			Tenure tenure, LocalDate disbursed) {
		return split(principal, interest(principal, ratePercent), List.of(tenure.endDate(disbursed)));
	}

	/** Spreads principal and interest over the dates; any rounding remainder goes on the last one. */
	static List<PlannedInstallment> split(BigDecimal principal, BigDecimal interest, List<LocalDate> dueDates) {
		int n = dueDates.size();
		BigDecimal total = principal.add(interest);
		BigDecimal each = wholeShillings(total.divide(BigDecimal.valueOf(n), 10, RoundingMode.HALF_UP));
		BigDecimal interestEach = wholeShillings(interest.divide(BigDecimal.valueOf(n), 10, RoundingMode.HALF_UP));

		List<PlannedInstallment> plan = new ArrayList<>();
		BigDecimal amountSoFar = Money.ZERO;
		BigDecimal interestSoFar = Money.ZERO;
		for (int i = 1; i <= n; i++) {
			boolean last = i == n;
			BigDecimal amount = last ? total.subtract(amountSoFar) : each;
			BigDecimal interestPart = last ? interest.subtract(interestSoFar) : interestEach.min(amount);
			plan.add(new PlannedInstallment(i, dueDates.get(i - 1), Money.orZero(amount), Money.orZero(interestPart),
					Money.orZero(amount.subtract(interestPart))));
			amountSoFar = amountSoFar.add(amount);
			interestSoFar = interestSoFar.add(interestPart);
		}
		return plan;
	}

	/**
	 * The lowest of: a multiple of the member's shares; a % of the pool total for one
	 * loan; and what's left under the cap on all loans together. Rounded down to whole KES.
	 */
	public BorrowingLimit limit(BigDecimal shares, BigDecimal poolTotal, BigDecimal principalOutstanding) {
		ChamaProperties.Loan settings = current.get();
		BigDecimal byShares = floor(shares.multiply(settings.sharesMultiple()));
		BigDecimal perLoanCap = floor(percentOf(poolTotal, settings.perLoanCapPercent()));
		BigDecimal headroom = floor(percentOf(poolTotal, settings.totalCapPercent()).subtract(principalOutstanding));
		return new BorrowingLimit(byShares, perLoanCap, headroom, settings);
	}

	public int requiredGuarantors(BigDecimal amount) {
		ChamaProperties.Loan settings = current.get();
		if (amount.compareTo(settings.twoGuarantorsAbove()) > 0) {
			return 2;
		}
		if (amount.compareTo(settings.oneGuarantorAbove()) > 0) {
			return 1;
		}
		return 0;
	}

	public ChamaProperties.Loan settings() {
		return current.get();
	}

	private static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
		return amount.multiply(percent).divide(HUNDRED, 10, RoundingMode.HALF_UP);
	}

	private static BigDecimal floor(BigDecimal amount) {
		return amount.setScale(0, RoundingMode.DOWN).max(BigDecimal.ZERO).setScale(2);
	}

	private static BigDecimal wholeShillings(BigDecimal amount) {
		return amount.setScale(0, RoundingMode.HALF_UP).setScale(2);
	}

	public record PlannedInstallment(int number, LocalDate dueDate, BigDecimal amount, BigDecimal interestPart,
			BigDecimal principalPart) {
	}
}

package com.personal.chama.contribution;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.personal.chama.loan.LoanEvent;
import com.personal.chama.loan.LoanService;
import com.personal.chama.member.Member;
import com.personal.chama.mgr.MgrPayout;

/**
 * A member's statement as a CSV file that opens in Excel or Google Sheets: shares,
 * month-by-month contributions, loans with their history, MGR payouts and debts between
 * members. Plain amounts (no "KES", no thousands separators) so spreadsheets can add them.
 */
final class MemberStatement {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

	private final StringBuilder csv = new StringBuilder("\uFEFF"); // so Excel reads it as UTF-8

	private MemberStatement() {
	}

	static String build(Member member, LocalDate today, BigDecimal shares, List<MemberMonth> history,
			List<LoanService.LoanView> loans, Function<LoanService.LoanView, List<LoanEvent>> timeline,
			List<MgrPayout> payouts, List<LoanService.DebtView> debts) {
		MemberStatement s = new MemberStatement();
		s.row("Family Chama - member statement");
		s.row("Member", member.getFullName());
		s.row("Member since", MONTH.format(member.getJoinDate()));
		s.row("Statement date", DATE.format(today));
		s.row("Total shares (KES)", amount(shares));
		s.row();

		s.row("CONTRIBUTIONS");
		s.row("Month", "Paid (KES)", "To savings (KES)", "To MGR (KES)", "Status");
		for (MemberMonth h : history) {
			s.row(MONTH.format(h.month()), amount(h.paid().total()), amount(h.paid().mmf()), amount(h.paid().mgr()),
					h.status().getLabel());
		}
		s.row();

		s.row("LOANS");
		if (loans.isEmpty()) {
			s.row("None");
		}
		for (LoanService.LoanView v : loans) {
			s.row("Loan", amount(v.loan().getPrincipal()) + " KES, " + v.loan().getTenure().getLabel() + " at "
					+ v.loan().getRatePercent().stripTrailingZeros().toPlainString() + "% flat",
					"Status: " + v.loan().getStatus().getLabel(),
					"Still owed (KES): " + amount(v.loan().isActive() ? v.remaining() : BigDecimal.ZERO));
			s.row("Date", "What happened", "Amount (KES)", "Details");
			for (LoanEvent e : timeline.apply(v)) {
				s.row(DATE.format(e.date()), e.label() + (e.reversed() ? " (reversed)" : ""),
						e.amount() == null ? "" : amount(e.amount()), e.detail());
			}
			s.row();
		}

		s.row("MERRY-GO-ROUND PAYOUTS");
		s.row("Month", "Paid on", "Pool (KES)", "Sent (KES)");
		if (payouts.isEmpty()) {
			s.row("None");
		}
		for (MgrPayout p : payouts) {
			s.row(MONTH.format(p.getMonth()), DATE.format(p.getPaidDate()), amount(p.getGross()), amount(p.net()));
		}

		if (!debts.isEmpty()) {
			s.row();
			s.row("DEBTS BETWEEN MEMBERS");
			s.row("Owed by", "Owed to", "Still owed (KES)");
			for (LoanService.DebtView d : debts) {
				s.row(d.debt().getLoan().getBorrower().getFullName(), d.debt().getGuarantor().getFullName(),
						amount(d.owed()));
			}
		}
		return s.csv.toString();
	}

	private static String amount(BigDecimal value) {
		return value == null ? "" : value.stripTrailingZeros().toPlainString();
	}

	private void row(String... cells) {
		for (int i = 0; i < cells.length; i++) {
			if (i > 0) {
				csv.append(',');
			}
			csv.append(escape(cells[i]));
		}
		csv.append("\r\n");
	}

	/** Quotes a cell when needed, and stops a spreadsheet treating text as a formula. */
	private static String escape(String value) {
		String v = value == null ? "" : value;
		if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0 && !v.matches("-?[0-9.]+")) {
			v = "'" + v;
		}
		if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
			v = "\"" + v.replace("\"", "\"\"") + "\"";
		}
		return v;
	}
}

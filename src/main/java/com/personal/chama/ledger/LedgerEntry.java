package com.personal.chama.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

import org.hibernate.annotations.Immutable;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.personal.chama.member.Member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One money movement. Append-only: Hibernate treats it as immutable and the database
 * refuses UPDATE and DELETE. Mistakes are fixed with {@link #reversal(String)}.
 */
@Entity
@Immutable
@Table(name = "ledger_entry")
@EntityListeners(AuditingEntityListener.class)
public class LedgerEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "entry_type", nullable = false, length = 40)
	private LedgerEntryType type;

	@Column(nullable = false, precision = 14, scale = 2)
	private BigDecimal amount;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "member_id")
	private Member member;

	@Column(name = "contribution_id")
	private Long contributionId;

	@Column(name = "fund_earning_id")
	private Long fundEarningId;

	@Column(name = "loan_id")
	private Long loanId;

	@Column(name = "repayment_id")
	private Long repaymentId;

	@Column(name = "late_fee_id")
	private Long lateFeeId;

	@Column(name = "debt_payment_id")
	private Long debtPaymentId;

	@Column(name = "period_month")
	private YearMonth month;

	@Column(name = "effective_date", nullable = false)
	private LocalDate effectiveDate;

	@Column(length = 500)
	private String note;

	@Column(name = "reverses_id")
	private Long reversesId;

	@CreatedDate
	@Column(name = "recorded_at", nullable = false)
	private Instant recordedAt;

	@CreatedBy
	@Column(name = "recorded_by", nullable = false, length = 50)
	private String recordedBy;

	protected LedgerEntry() {
	}

	private LedgerEntry(LedgerEntryType type, BigDecimal amount, Member member, YearMonth month,
			LocalDate effectiveDate, String note) {
		if (amount.signum() == 0) {
			throw new IllegalArgumentException("A ledger entry cannot be zero");
		}
		this.type = type;
		this.amount = amount;
		this.member = member;
		this.month = month;
		this.effectiveDate = effectiveDate;
		this.note = note;
	}

	public static LedgerEntry of(LedgerEntryType type, BigDecimal amount, Member member, YearMonth month,
			LocalDate effectiveDate, String note) {
		return new LedgerEntry(type, amount, member, month, effectiveDate, note);
	}

	public static LedgerEntry forContribution(LedgerEntryType type, BigDecimal amount, Member member,
			YearMonth month, LocalDate paidDate, Long contributionId) {
		LedgerEntry entry = new LedgerEntry(type, amount, member, month, paidDate, null);
		entry.contributionId = contributionId;
		return entry;
	}

	public static LedgerEntry forFundEarning(BigDecimal amount, YearMonth month, LocalDate date,
			Long fundEarningId, String note) {
		LedgerEntry entry = new LedgerEntry(LedgerEntryType.FUND_EARNINGS, amount, null, month, date, note);
		entry.fundEarningId = fundEarningId;
		return entry;
	}

	public static LedgerEntry forLoan(LedgerEntryType type, BigDecimal amount, Member borrower, LocalDate date,
			Long loanId, Long repaymentId, String note) {
		LedgerEntry entry = new LedgerEntry(type, amount, borrower, null, date, note);
		entry.loanId = loanId;
		entry.repaymentId = repaymentId;
		return entry;
	}

	public static LedgerEntry forLateFee(LedgerEntryType type, BigDecimal amount, Member borrower, LocalDate date,
			Long loanId, Long lateFeeId, String note) {
		LedgerEntry entry = forLoan(type, amount, borrower, date, loanId, null, note);
		entry.lateFeeId = lateFeeId;
		return entry;
	}

	public static LedgerEntry forDebtPayment(BigDecimal amount, Member guarantor, LocalDate date, Long loanId,
			Long debtPaymentId, String note) {
		LedgerEntry entry = forLoan(LedgerEntryType.GUARANTOR_DEBT_REPAID, amount, guarantor, date, loanId, null, note);
		entry.debtPaymentId = debtPaymentId;
		return entry;
	}

	/**
	 * An entry with the opposite amount that cancels this one. Both stay in the history.
	 * It carries the original's effective date, so balances "as at" any date are as if
	 * the mistake never happened; recordedAt shows when the correction was made.
	 */
	LedgerEntry reversal(String reason) {
		if (reversesId != null) {
			throw new IllegalStateException("A reversal cannot itself be reversed; record a new entry instead");
		}
		LedgerEntry reversal = new LedgerEntry(type, amount.negate(), member, month, effectiveDate, "Reversal: " + reason);
		reversal.contributionId = contributionId;
		reversal.fundEarningId = fundEarningId;
		reversal.loanId = loanId;
		reversal.repaymentId = repaymentId;
		reversal.lateFeeId = lateFeeId;
		reversal.debtPaymentId = debtPaymentId;
		reversal.reversesId = id;
		return reversal;
	}

	public boolean isReversal() {
		return reversesId != null;
	}

	public Long getId() {
		return id;
	}

	public LedgerEntryType getType() {
		return type;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public Member getMember() {
		return member;
	}

	public Long getContributionId() {
		return contributionId;
	}

	public Long getFundEarningId() {
		return fundEarningId;
	}

	public Long getLoanId() {
		return loanId;
	}

	public Long getRepaymentId() {
		return repaymentId;
	}

	public Long getLateFeeId() {
		return lateFeeId;
	}

	public Long getDebtPaymentId() {
		return debtPaymentId;
	}

	public YearMonth getMonth() {
		return month;
	}

	public LocalDate getEffectiveDate() {
		return effectiveDate;
	}

	public String getNote() {
		return note;
	}

	public Long getReversesId() {
		return reversesId;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}

	public String getRecordedBy() {
		return recordedBy;
	}
}

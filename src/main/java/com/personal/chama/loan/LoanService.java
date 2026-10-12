package com.personal.chama.loan;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.chama.common.Money;
import com.personal.chama.contribution.ContributionService;
import com.personal.chama.decision.GroupDecision;
import com.personal.chama.decision.GroupDecisionRepository;
import com.personal.chama.ledger.LedgerEntry;
import com.personal.chama.ledger.LedgerEntryRepository;
import com.personal.chama.ledger.LedgerEntryType;
import com.personal.chama.ledger.LedgerService;
import com.personal.chama.member.Member;
import com.personal.chama.member.MemberNotFoundException;
import com.personal.chama.member.MemberRepository;

/** Borrowing limits, guarantor checks, disbursement and repayments (brief 4.4). */
@Service
@Transactional(readOnly = true)
public class LoanService {

	private final LoanRepository loans;
	private final GuaranteeRepository guarantees;
	private final InstallmentRepository installments;
	private final RepaymentRepository repayments;
	private final LoanSignatureRepository signatures;
	private final MemberRepository members;
	private final LedgerService ledger;
	private final LedgerEntryRepository ledgerEntries;
	private final ContributionService contributions;
	private final LoanCalculator calculator;
	private final AuditorAware<String> auditor;
	private final Clock clock;
	private final LoanHistory history;
	private final LatePaymentService latePayments;
	private final GuarantorDebtRepository debts;
	private final GuarantorDebtPaymentRepository debtPayments;
	private final GroupDecisionRepository decisions;

	public LoanService(LoanRepository loans, GuaranteeRepository guarantees, InstallmentRepository installments,
			RepaymentRepository repayments, LoanSignatureRepository signatures, MemberRepository members,
			LedgerService ledger, LedgerEntryRepository ledgerEntries, ContributionService contributions,
			LoanCalculator calculator, AuditorAware<String> auditor, Clock clock, LoanHistory history,
			LatePaymentService latePayments, GuarantorDebtRepository debts, GuarantorDebtPaymentRepository debtPayments,
			GroupDecisionRepository decisions) {
		this.loans = loans;
		this.guarantees = guarantees;
		this.installments = installments;
		this.repayments = repayments;
		this.signatures = signatures;
		this.members = members;
		this.ledger = ledger;
		this.ledgerEntries = ledgerEntries;
		this.contributions = contributions;
		this.calculator = calculator;
		this.auditor = auditor;
		this.clock = clock;
		this.history = history;
		this.latePayments = latePayments;
		this.debts = debts;
		this.debtPayments = debtPayments;
		this.decisions = decisions;
	}

	public LocalDate today() {
		return LocalDate.now(clock);
	}

	// ---------------------------------------------------------------- eligibility

	/** Everything the new-loan screen shows about a would-be borrower. */
	public Assessment assess(Long borrowerId) {
		Member borrower = member(borrowerId);
		BigDecimal shares = ledger.sharesOf(borrowerId);
		BorrowingLimit limit = calculator.limit(shares, ledger.poolTotal(), ledger.principalOutstanding());
		Optional<Loan> activeLoan = loans.findFirstByBorrowerIdAndStatusIn(borrowerId, LoanStatus.OPEN);
		BigDecimal overdue = overdueAmount(borrowerId);

		List<String> blockers = new ArrayList<>();
		if (!borrower.isActive()) {
			blockers.add(borrower.getFullName() + " is not an active member.");
		}
		activeLoan.ifPresent(l -> blockers.add(borrower.getFullName() + " already has an active loan (one at a time)."));
		if (overdue.signum() > 0) {
			blockers.add(borrower.getFullName() + " owes KES " + Money.format(overdue) + " that is past due.");
		}
		if (limit.limit().signum() <= 0) {
			blockers.add("The borrowing limit is zero (" + limit.bindingDescription() + ").");
		}
		return new Assessment(borrower, shares, limit, activeLoan.orElse(null), overdue, blockers);
	}

	/**
	 * Arrears: contributions owed past their due day, plus loan installments past their
	 * due date (Phase 3 plan, Q4).
	 */
	public BigDecimal overdueAmount(Long memberId) {
		BigDecimal loanOverdue = loans.findFirstByBorrowerIdAndStatusIn(memberId, LoanStatus.OPEN)
				.map(l -> history.dueNow(l, history.schedule(l)))
				.orElse(Money.ZERO);
		return contributions.overdueAmount(memberId).add(loanOverdue);
	}

	/** Every other active member, with the reason they can't guarantee this borrower (or none). */
	public List<GuarantorOption> guarantorOptions(Long borrowerId) {
		Member borrower = member(borrowerId);
		return members.findByActiveTrueOrderByFullNameAsc().stream()
				.filter(m -> !m.getId().equals(borrowerId))
				.map(m -> new GuarantorOption(m, ledger.sharesOf(m.getId()), guarantorProblem(m, borrower)))
				.toList();
	}

	/** Why this member can't guarantee this borrower, or null if they can (brief 4.4). */
	String guarantorProblem(Member guarantor, Member borrower) {
		if (guarantor.getId().equals(borrower.getId())) {
			return "A borrower can't guarantee their own loan.";
		}
		if (!guarantor.isActive()) {
			return guarantor.getFullName() + " is not an active member.";
		}
		BigDecimal shares = ledger.sharesOf(guarantor.getId());
		BigDecimal minimum = calculator.settings().guarantorMinShares();
		if (shares.compareTo(minimum) < 0) {
			return guarantor.getFullName() + " has KES " + Money.format(shares) + " in shares; a guarantor needs at least "
					+ Money.format(minimum) + ".";
		}
		BigDecimal overdue = overdueAmount(guarantor.getId());
		if (overdue.signum() > 0) {
			return guarantor.getFullName() + " owes KES " + Money.format(overdue) + " that is past due.";
		}
		List<Guarantee> theirs = guarantees.findActiveByGuarantor(guarantor.getId());
		if (!theirs.isEmpty()) {
			return guarantor.getFullName() + " already guarantees " + theirs.get(0).getLoan().getBorrower().getFullName()
					+ "'s loan (one at a time).";
		}
		boolean borrowerBacksGuarantor = guarantees.findActiveByGuarantor(borrower.getId()).stream()
				.anyMatch(g -> g.getLoan().getBorrower().getId().equals(guarantor.getId()));
		if (borrowerBacksGuarantor) {
			return borrower.getFullName() + " is guaranteeing " + guarantor.getFullName()
					+ "'s loan, so they can't guarantee each other.";
		}
		return null;
	}

	// ---------------------------------------------------------------- disbursement

	/** A preview of the terms for the form, without saving anything. */
	public Terms terms(BigDecimal amount, Tenure tenure, LocalDate disbursed, LoanForm.Schedule schedule) {
		BigDecimal rate = calculator.ratePercent(tenure);
		List<LoanCalculator.PlannedInstallment> plan = schedule == LoanForm.Schedule.SINGLE_AT_END
				? LoanCalculator.singlePaymentAtEnd(amount, rate, tenure, disbursed)
				: LoanCalculator.schedule(amount, rate, tenure, disbursed);
		BigDecimal interest = LoanCalculator.interest(amount, rate);
		return new Terms(amount, tenure, rate, interest, amount.add(interest), plan, calculator.requiredGuarantors(amount));
	}

	@Transactional
	public Loan disburse(LoanForm form) {
		Member borrower = members.findByIdForUpdate(form.getBorrowerId())
				.orElseThrow(() -> new MemberNotFoundException(form.getBorrowerId()));
		BigDecimal amount = Money.orZero(form.getAmount());
		if (form.getDisbursedDate().isAfter(today())) {
			throw new LoanException("The disbursement date can't be in the future.");
		}
		if (form.getKind() == LoanKind.NEW && form.getSchedule() != LoanForm.Schedule.STANDARD) {
			throw new LoanException("A new loan uses the standard schedule.");
		}
		List<Member> chosen = guarantorsFrom(form, borrower);

		if (form.getKind().checksRules()) {
			checkPolicy(borrower, amount, chosen);
		} else {
			if (form.getNote() == null || form.getNote().isBlank()) {
				throw new LoanException("Say why this loan is recorded as agreed rather than checked against the policy.");
			}
			if (loans.findFirstByBorrowerIdAndStatusIn(borrower.getId(), LoanStatus.OPEN).isPresent()) {
				throw new LoanException(borrower.getFullName() + " already has an active loan (one at a time).");
			}
		}

		Terms terms = terms(amount, form.getTenure(), form.getDisbursedDate(), form.getSchedule());
		if (terms.installments().stream().anyMatch(i -> i.amount().signum() <= 0)) {
			throw new LoanException("That amount is too small to split into " + terms.installments().size() + " installments.");
		}
		Loan loan = new Loan(borrower, amount, form.getTenure(), terms.ratePercent(), form.getDisbursedDate(),
				form.getKind(), blankToNull(form.getPurpose()), blankToNull(form.getNote()), form.getMethod().strip(),
				blankToNull(form.getReference()));
		terms.installments().forEach(loan::addInstallment);
		chosen.forEach(loan::addGuarantor);
		loan = loans.save(loan);

		ledger.post(LedgerEntry.forLoan(LedgerEntryType.LOAN_DISBURSED, amount.negate(), borrower,
				form.getDisbursedDate(), loan.getId(), null, "Loan to " + borrower.getFullName()));
		return loan;
	}

	private void checkPolicy(Member borrower, BigDecimal amount, List<Member> chosen) {
		Assessment assessment = assess(borrower.getId());
		if (!assessment.blockers().isEmpty()) {
			throw new LoanException(assessment.blockers().get(0));
		}
		if (amount.compareTo(assessment.limit().limit()) > 0) {
			throw new LoanException("That is more than the borrowing limit of KES " + Money.format(assessment.limit().limit())
					+ " (" + assessment.limit().bindingDescription() + ").");
		}
		if (amount.compareTo(ledger.mmfBalance()) > 0) {
			throw new LoanException("The pool only has KES " + Money.format(ledger.mmfBalance()) + " in cash.");
		}
		int required = calculator.requiredGuarantors(amount);
		if (chosen.size() != required) {
			throw new LoanException(required == 0
					? "A loan of this size doesn't need a guarantor."
					: "A loan of this size needs " + required + (required == 1 ? " guarantor." : " guarantors."));
		}
		for (Member guarantor : chosen) {
			String problem = guarantorProblem(guarantor, borrower);
			if (problem != null) {
				throw new LoanException(problem);
			}
		}
	}

	private List<Member> guarantorsFrom(LoanForm form, Member borrower) {
		List<Long> ids = form.chosenGuarantors();
		if (new HashSet<>(ids).size() != ids.size()) {
			throw new LoanException("The same person can't be chosen as guarantor twice.");
		}
		List<Member> chosen = new ArrayList<>();
		for (Long id : ids) {
			if (id.equals(borrower.getId())) {
				throw new LoanException("A borrower can't guarantee their own loan.");
			}
			chosen.add(member(id));
		}
		return chosen;
	}

	/** Undoes a loan entered by mistake. Only possible before any repayment. */
	@Transactional
	public void cancel(Long loanId, String reason) {
		Loan loan = loan(loanId);
		if (!loan.isActive()) {
			throw new LoanException("Only an active loan can be cancelled.");
		}
		if (!repayments.findByLoanIdAndVoidedAtIsNullOrderByPaidDateAscIdAsc(loanId).isEmpty()) {
			throw new LoanException("This loan has repayments. Correct those first.");
		}
		for (LedgerEntry entry : ledgerEntries.findByLoanIdAndTypeAndReversesIdIsNull(loanId, LedgerEntryType.LOAN_DISBURSED)) {
			if (!ledger.isReversed(entry.getId())) {
				ledger.reverse(entry.getId(), reason);
			}
		}
		loan.cancel(reason);
	}

	// ---------------------------------------------------------------- repayments

	@Transactional
	public Repayment repay(Long loanId, BigDecimal amount, LocalDate paidDate, String method, String reference) {
		Loan loan = loan(loanId);
		members.findByIdForUpdate(loan.getBorrower().getId());
		latePayments.reconcile(loan);
		if (!loan.isActive()) {
			throw new LoanException("This loan is not being repaid any more.");
		}
		if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
			throw new LoanException("Enter an amount more than zero, with at most 2 decimal places.");
		}
		if (paidDate == null || paidDate.isAfter(today())) {
			throw new LoanException("The payment date can't be in the future.");
		}
		LocalDate latest = history.latestEventDate(loan);
		if (paidDate.isBefore(latest)) {
			throw new LoanException("The payment date can't be before " + latest
					+ " (the loan's latest payment, change or disbursement).");
		}
		if (method == null || method.isBlank()) {
			throw new LoanException("Enter how it was paid.");
		}
		// Replay up to the payment date: a fee charged after that date isn't paid by it.
		RepaymentSchedule.Allocation allocation = history.scheduleAsOf(loan, paidDate)
				.apply(Money.orZero(amount), paidDate);

		Repayment repayment = repayments.save(new Repayment(loan, Money.orZero(amount), paidDate, method.strip(),
				blankToNull(reference)));
		postAllocation(loan, allocation, paidDate, repayment.getId());
		afterPayment(loan);
		return repayment;
	}

	/** Ledger entries for the parts of a payment: late fee, interest, principal - all into the pool. */
	void postAllocation(Loan loan, RepaymentSchedule.Allocation allocation, LocalDate date, Long repaymentId) {
		Member borrower = loan.getBorrower();
		if (allocation.fee().signum() > 0) {
			ledger.post(LedgerEntry.forLoan(LedgerEntryType.LATE_FEE_PAID, allocation.fee(), borrower, date,
					loan.getId(), repaymentId, null));
		}
		if (allocation.interest().signum() > 0) {
			ledger.post(LedgerEntry.forLoan(LedgerEntryType.LOAN_INTEREST_PAID, allocation.interest(), borrower, date,
					loan.getId(), repaymentId, null));
		}
		if (allocation.principal().signum() > 0) {
			ledger.post(LedgerEntry.forLoan(LedgerEntryType.LOAN_PRINCIPAL_REPAID, allocation.principal(), borrower,
					date, loan.getId(), repaymentId, null));
		}
	}

	/** Brings fees and status up to date, and closes the loan once nothing is owed. */
	void afterPayment(Loan loan) {
		latePayments.reconcile(loan);
		if (history.schedule(loan).isFullyPaid()) {
			loan.markRepaid();
		}
	}

	/**
	 * Reverses a repayment (or a recovery from shares). Only the most recent one can be
	 * corrected, and not once the schedule has been changed after it, so the way earlier
	 * payments were split never changes.
	 */
	@Transactional
	public void voidRepayment(Long loanId, Long repaymentId, String reason) {
		Loan loan = loan(loanId);
		List<Repayment> standing = history.standingRepayments(loanId);
		if (standing.isEmpty() || !standing.get(standing.size() - 1).getId().equals(repaymentId)) {
			throw new LoanException("Only the most recent repayment can be corrected. Correct the later ones first.");
		}
		Repayment repayment = standing.get(standing.size() - 1);
		boolean changedSince = history.changes(loanId).stream()
				.anyMatch(c -> c.getRecordedAt().isAfter(repayment.getRecordedAt()));
		if (changedSince) {
			throw new LoanException("The loan's schedule was changed after this repayment, so it can't be corrected.");
		}
		for (GuarantorDebt debt : debts.findByRepaymentId(repaymentId)) {
			if (!debtPayments.findByDebtIdAndVoidedAtIsNull(debt.getId()).isEmpty()) {
				throw new LoanException("The borrower has already repaid part of this to the guarantor. Correct that first.");
			}
		}
		for (LedgerEntry entry : ledgerEntries.findByRepaymentIdAndReversesIdIsNull(repaymentId)) {
			if (!ledger.isReversed(entry.getId())) {
				ledger.reverse(entry.getId(), reason);
			}
		}
		repayment.voidWith(reason, auditor.getCurrentAuditor().orElse("system"), Instant.now(clock));
		if (repayment.isRecovery()) {
			loan.clearPoolLoss();
		}
		if (loan.getStatus() == LoanStatus.REPAID) {
			loan.reopen(loan.getDefaultedOn() != null ? LoanStatus.DEFAULTED
					: wasRestructured(loanId) ? LoanStatus.RESTRUCTURED : LoanStatus.ACTIVE);
		}
		latePayments.reconcile(loan);
	}

	boolean wasRestructured(Long loanId) {
		return decisions.findByLoanIdOrderByDecidedOnDescIdDesc(loanId).stream()
				.anyMatch(d -> d.getKind() == GroupDecision.Kind.RESTRUCTURE);
	}

	// ---------------------------------------------------------------- agreement initials

	@Transactional
	public void recordInitials(Long loanId, LoanSignature.Role role, Long memberId, String treasurerName,
			String initials, LocalDate signedDate) {
		Loan loan = loan(loanId);
		if (initials == null || initials.isBlank() || initials.strip().length() > 10) {
			throw new LoanException("Enter the initials (up to 10 characters).");
		}
		if (signedDate == null || signedDate.isAfter(today())) {
			throw new LoanException("Enter the date they initialled, not in the future.");
		}
		Member member = null;
		String name;
		switch (role) {
			case BORROWER -> {
				member = loan.getBorrower();
				name = member.getFullName();
			}
			case GUARANTOR -> {
				member = guarantees.findByLoanId(loanId).stream().map(Guarantee::getGuarantor)
						.filter(g -> g.getId().equals(memberId)).findFirst()
						.orElseThrow(() -> new LoanException("That member is not a guarantor of this loan."));
				name = member.getFullName();
			}
			default -> {
				if (treasurerName == null || treasurerName.isBlank()) {
					throw new LoanException("Enter the treasurer's name.");
				}
				name = treasurerName.strip();
			}
		}
		Long id = member == null ? null : member.getId();
		boolean alreadySigned = signatures.findByLoanId(loanId).stream()
				.anyMatch(s -> s.getRole() == role && Objects.equals(s.getMemberId(), id));
		if (alreadySigned) {
			throw new LoanException(name + " has already initialled this agreement.");
		}
		signatures.save(new LoanSignature(loan, role, member, name, initials.strip().toUpperCase(), signedDate));
	}

	// ---------------------------------------------------------------- reading

	public LoanView view(Long loanId) {
		Loan loan = loan(loanId);
		RepaymentSchedule schedule = history.schedule(loan);
		List<Repayment> standing = history.standingRepayments(loanId);
		Map<Long, String> lineStatus = new HashMap<>();
		for (RepaymentSchedule.Line line : schedule.lines()) {
			Installment i = line.installment();
			String status;
			if (!line.isActive()) {
				status = i.isSuperseded() ? "Replaced" : "";
			} else if (line.isPaid()) {
				status = "Paid";
			} else if (history.isPastDeadline(i)) {
				status = "Overdue";
			} else if (history.isInGrace(i)) {
				status = "In grace";
			} else {
				status = "";
			}
			lineStatus.put(i.getId(), status);
		}
		List<DebtView> debtViews = debts.findByLoanIdOrderByIdAsc(loanId).stream()
				.filter(d -> standing.stream().anyMatch(r -> r.getId().equals(d.getRepaymentId())))
				.map(this::debtView).toList();
		return new LoanView(loan, loan.getBorrower(),
				guarantees.findByLoanId(loanId).stream().map(Guarantee::getGuarantor).toList(),
				schedule, repayments.findByLoanIdOrderByPaidDateDescIdDesc(loanId),
				standing.isEmpty() ? null : standing.get(standing.size() - 1).getId(),
				ledger.principalOutstandingFor(loanId), ledger.interestPaidFor(loanId), flag(loan, schedule),
				signatures.findByLoanId(loanId), lineStatus, history.fees(loanId), debtViews,
				decisions.findByLoanIdOrderByDecidedOnDescIdDesc(loanId),
				loan.isActive() ? history.dueNow(loan, schedule) : Money.ZERO,
				history.recoveryAvailable(loan, schedule));
	}

	DebtView debtView(GuarantorDebt debt) {
		List<GuarantorDebtPayment> payments = debtPayments.findByDebtIdOrderByPaidDateAscIdAsc(debt.getId());
		BigDecimal repaid = payments.stream().filter(p -> !p.isVoided()).map(GuarantorDebtPayment::getAmount)
				.reduce(Money.ZERO, BigDecimal::add);
		return new DebtView(debt, repaid, debt.getAmount().subtract(repaid), payments);
	}

	/** Everything that happened to a loan, oldest first, for the borrower's page. */
	public List<LoanEvent> timeline(LoanView v) {
		Loan loan = v.loan();
		List<LoanEvent> events = new java.util.ArrayList<>();
		events.add(new LoanEvent(loan.getDisbursedDate(), "Disbursed", loan.getPrincipal(),
				loan.getTenure().getLabel() + " at " + loan.getRatePercent().stripTrailingZeros().toPlainString()
						+ "% flat" + (loan.getDisbursementMethod() != null ? " · by " + loan.getDisbursementMethod() : ""),
				"grey", false));
		Map<Long, String> guarantorNames = new HashMap<>();
		v.guarantors().forEach(g -> guarantorNames.put(g.getId(), g.getFullName()));
		for (Repayment r : v.repayments()) {
			String label = switch (r.getSource()) {
				case BORROWER -> "Repayment";
				case BORROWER_SHARES -> "Recovered from own shares";
				case GUARANTOR_SHARES -> "Recovered from " + guarantorNames.getOrDefault(r.getGuarantorId(), "a guarantor")
						+ "'s shares";
			};
			String detail = r.isRecovery() ? "" : r.getMethod() + (r.getReference() != null ? " · " + r.getReference() : "");
			if (r.isVoided()) {
				detail = (detail.isEmpty() ? "" : detail + " · ") + "reversed: " + r.getVoidReason();
			}
			events.add(new LoanEvent(r.getPaidDate(), label, r.getAmount(), detail,
					r.isRecovery() ? "amber" : "green", r.isVoided()));
		}
		for (LateFee fee : v.fees()) {
			Installment i = v.installmentOf(fee);
			events.add(new LoanEvent(fee.getChargedOn(), "Late fee charged", fee.getAmount(),
					i == null ? "" : "installment " + i.getNumber() + " due " + i.getDueDate() + " was missed", "red", false));
		}
		if (loan.getDefaultedOn() != null) {
			events.add(new LoanEvent(loan.getDefaultedOn(), "Defaulted", null,
					"two payments missed in a row: the whole balance became due", "red", false));
		}
		for (GroupDecision d : v.decisions()) {
			events.add(new LoanEvent(d.getDecidedOn(), d.getKind().getLabel(), null,
					d.getSummary() + " · " + d.getHowAgreed(), "grey", false));
		}
		if (loan.getStatus() == LoanStatus.REPAID) {
			v.repayments().stream().filter(r -> !r.isVoided()).map(Repayment::getPaidDate).max(LocalDate::compareTo)
					.ifPresent(date -> events.add(new LoanEvent(date, "Repaid in full", null, "", "green", false)));
		}
		if (loan.getStatus() == LoanStatus.CANCELLED) {
			events.add(new LoanEvent(loan.getDisbursedDate(), "Cancelled", null, loan.getCancelReason(), "grey", false));
		}
		// Same day: in the order things happen (disbursed, fees, payments, decisions, outcomes).
		List<String> order = List.of("Disbursed", "Late fee charged", "Defaulted");
		events.sort(java.util.Comparator.comparing(LoanEvent::date)
				.thenComparing(e -> order.contains(e.label()) ? order.indexOf(e.label()) : order.size()));
		return events;
	}

	/** Debts the member owes guarantors (as borrower) and is owed (as guarantor), still standing. */
	public List<DebtView> debtsInvolving(Long memberId) {
		Map<Long, GuarantorDebt> all = new LinkedHashMap<>();
		debts.findByLoanBorrowerIdOrderByIdAsc(memberId).forEach(d -> all.put(d.getId(), d));
		debts.findByGuarantorIdOrderByIdAsc(memberId).forEach(d -> all.put(d.getId(), d));
		return all.values().stream()
				.filter(d -> repayments.findById(d.getRepaymentId()).map(r -> !r.isVoided()).orElse(false))
				.map(this::debtView).toList();
	}

	public List<LoanView> active() {
		return loans.findByStatusInOrderByDisbursedDateAsc(LoanStatus.OPEN).stream().map(l -> view(l.getId())).toList();
	}

	public List<LoanView> all() {
		return loans.findAllByOrderByDisbursedDateDescIdDesc().stream().map(l -> view(l.getId())).toList();
	}

	public List<LoanView> forBorrower(Long memberId) {
		return loans.findByBorrowerIdOrderByDisbursedDateDesc(memberId).stream().map(l -> view(l.getId())).toList();
	}

	/** Active loans this member is guaranteeing. */
	public List<Loan> guaranteedBy(Long memberId) {
		return guarantees.findActiveByGuarantor(memberId).stream().map(Guarantee::getLoan).toList();
	}

	RepaymentSchedule schedule(Loan loan) {
		return history.schedule(loan);
	}

	private LoanFlag flag(Loan loan, RepaymentSchedule schedule) {
		RepaymentSchedule.Line next = schedule.nextDue();
		if (!loan.isActive() || next == null) {
			return LoanFlag.CLOSED;
		}
		boolean overdue = loan.getStatus() == LoanStatus.DEFAULTED || schedule.activeLines().stream()
				.anyMatch(l -> !l.isPaid() && history.isPastDeadline(l.installment()));
		if (overdue) {
			return LoanFlag.OVERDUE;
		}
		LocalDate due = next.installment().getDueDate();
		if (history.isInGrace(next.installment()) || !due.isAfter(today().plusDays(calculator.settings().dueSoonDays()))) {
			return LoanFlag.DUE_SOON;
		}
		return LoanFlag.ON_TRACK;
	}

	private Loan loan(Long id) {
		return loans.findWithBorrowerById(id).orElseThrow(() -> new LoanException("No such loan."));
	}

	private Member member(Long id) {
		return members.findById(id).orElseThrow(() -> new MemberNotFoundException(id));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	// ---------------------------------------------------------------- views

	public record Assessment(Member borrower, BigDecimal shares, BorrowingLimit limit, Loan activeLoan,
			BigDecimal overdue, List<String> blockers) {

		public boolean eligible() {
			return blockers.isEmpty();
		}
	}

	public record GuarantorOption(Member member, BigDecimal shares, String problem) {

		public boolean eligible() {
			return problem == null;
		}
	}

	public record Terms(BigDecimal principal, Tenure tenure, BigDecimal ratePercent, BigDecimal interest,
			BigDecimal total, List<LoanCalculator.PlannedInstallment> installments, int requiredGuarantors) {
	}

	public record DebtView(GuarantorDebt debt, BigDecimal repaid, BigDecimal owed, List<GuarantorDebtPayment> payments) {

		/** Only the latest standing payment can be corrected. */
		public Long correctablePaymentId() {
			return payments.stream().filter(p -> !p.isVoided()).reduce((a, b) -> b).map(GuarantorDebtPayment::getId)
					.orElse(null);
		}
	}

	public record LoanView(Loan loan, Member borrower, List<Member> guarantors, RepaymentSchedule schedule,
			List<Repayment> repayments, Long correctableRepaymentId, BigDecimal principalOutstanding,
			BigDecimal interestPaid, LoanFlag flag, List<LoanSignature> signatures, Map<Long, String> lineStatus,
			List<LateFee> fees, List<DebtView> debts, List<GroupDecision> decisions, BigDecimal dueNow,
			boolean recoveryAvailable) {

		public String statusOf(RepaymentSchedule.Line line) {
			return lineStatus.getOrDefault(line.installment().getId(), "");
		}

		/** What's still owed on a late fee. */
		public BigDecimal feeOwedOn(LateFee fee) {
			RepaymentSchedule.Line line = schedule.lineFor(fee.getInstallmentId());
			return line == null ? fee.payable() : fee.payable().subtract(line.feePaid()).max(BigDecimal.ZERO);
		}

		public Installment installmentOf(LateFee fee) {
			RepaymentSchedule.Line line = schedule.lineFor(fee.getInstallmentId());
			return line == null ? null : line.installment();
		}

		public BigDecimal remaining() {
			return schedule.remaining();
		}

		public RepaymentSchedule.Line nextDue() {
			return schedule.nextDue();
		}

		/** A party's initials, or null. Role is BORROWER, GUARANTOR or TREASURER (as text, for templates). */
		public LoanSignature signature(String role, Long memberId) {
			return signatures.stream()
					.filter(s -> s.getRole().name().equals(role) && (memberId == null || memberId.equals(s.getMemberId())))
					.findFirst().orElse(null);
		}
	}
}

# Phase 3 plan: loans

Status: **draft for review**. Answer the questions in section 1, then approve
at the bottom.

Goal: work out each member's borrowing limit, preview the interest and
installments, check guarantors properly, disburse loans and record
repayments - all through the ledger. Late fees, recovery, extensions and
restructures come in Phase 4; this phase lays the ground for them.

---------------------------------------------------------------------
## 1. QUESTIONS (answer after each "Answer:")
---------------------------------------------------------------------

Q1. LOAN AGREEMENT PAGE. The policy says every loan gets "a simple one-page
    signed form: amount, rate, tenure, guarantor(s), and date". Suggested:
    a printable page for each loan with those details, the repayment
    schedule, and signature lines for the borrower, guarantor(s) and
    treasurer. Print it, sign it, keep it. OK?
    Answer: ok. but the signature can also be initials on the digital form to avoid the need to print and scan back the copy

Q2. HOW A REPAYMENT IS APPLIED. Suggested: it goes to the OLDEST unpaid
    installment first, and within that installment: late fees, then
    interest, then principal (decision L5). Anything left moves on to the
    next installment. OK?
    Answer: ok

Q3. PAYING EARLY. Repaying early doesn't reduce the fee (brief). So paying
    off a loan early means paying all the remaining principal AND all the
    remaining interest; the loan is then marked Repaid. Paying more than
    the whole remaining balance is refused. OK?
    Answer: ok

Q4. "NO ARREARS" for borrowers and guarantors. Suggested meaning: no
    contribution owed for a month whose due day (10th) has passed, AND no
    loan installment overdue. Opted-out months don't count. OK?
    Answer: ok

Q5. WHOLE SHILLINGS. Loan amounts are whole KES (no cents). Interest and
    installments are already rounded to whole KES (brief). OK?
    Answer: ok

Q6. MUM'S LOAN (disbursed this weekend). At go-live it is entered as an
    existing loan "checked against the policy", with its real date. The
    checks use the figures in the app at that moment (shares, pool),
    which should match the weekend's figures if the opening balances and
    October payments are entered first. OK?
    Answer: ok

---------------------------------------------------------------------
## 2. THE RULES (brief 4.4 + decisions)
---------------------------------------------------------------------

WHO CAN BORROW
- No money owed right now (Q4), and no active loan (one loan at a time).
- Active members only.

BORROWING LIMIT = the LOWEST of:
  a) 3 x the member's shares
  b) 50% of the pool total            (per-loan cap, setting)
  c) 60% of the pool total, minus all principal already out on loans
                                       (total-outstanding cap, setting)
  Pool total = MMF cash + principal still out on loans (decision P1).
  The screen shows all three and names the one that applies.

  Example: shares 34,000; cash 140,000; 50,000 already out on loans.
    Pool total = 190,000
    a) 3 x 34,000            = 102,000
    b) 50% x 190,000         =  95,000
    c) 60% x 190,000 - 50,000 = 64,000   <- limit: 64,000 (total-outstanding cap)

INTEREST - a flat fee, worked out once on the original amount:
  Tenure        Rate    Repaid as
  2 weeks       1.5%    one payment on day 14
  1 month       3%      one payment after 1 month
  2 or 3 months 5%      equal monthly installments
  4, 5, 6 months 8%     equal monthly installments
- The rate is saved on the loan when it's disbursed, so later changes to
  the settings never change a loan that's already out.
- Interest and installments are rounded to whole KES (half up). If the
  installments don't divide evenly, the remainder goes on the LAST one so
  the total is exact.
- Repaying early does not reduce the fee.

GUARANTORS
  Up to 20,000          none
  20,001 - 35,000       1 guarantor
  Above 35,000          2 guarantors
Real validation, not helper text - a guarantor:
- has at least 20,000 in shares and no arrears (Q4);
- backs only one active loan at a time;
- is not the borrower;
- is not someone the borrower is currently guaranteeing, and is not
  currently borrowing with the borrower as their guarantor (no circular
  guarantees).

EXISTING LOANS (decision L8)
- "Checked against the policy": all the rules above, with the real
  disbursement date (e.g. mum's loan).
- "Recorded as agreed": rules not re-checked, a custom schedule allowed
  (e.g. one payment of 54,000 at the end), and a note saying why.
- Both go into the ledger with their real dates. Loans out on 30 Sep are
  then included automatically in the opening-balances sum.

---------------------------------------------------------------------
## 3. HOW LOANS ARE RECORDED
---------------------------------------------------------------------

NEW LEDGER ENTRY TYPES
  LOAN_DISBURSED         money out of the pool          pool -
  LOAN_PRINCIPAL_REPAID  principal back into the pool   pool +
  LOAN_INTEREST_PAID     interest into the pool         pool +
  (Late fees, recovery from shares and write-offs come in Phase 4.)

  Pool total = cash + principal still out, so disbursing a loan moves
  money from "cash" to "out on loan" without changing the total.

NEW TABLES
  loan        borrower, principal, tenure, rate and interest (fixed at
              disbursement), disbursed date, status (Active / Repaid,
              plus Phase 4 statuses), purpose (optional), kind (new /
              existing checked / existing as agreed), note
  guarantee   loan, guarantor, status
  installment loan, number, due date, amount, of which interest and
              principal, amounts paid, date fully paid
  repayment   loan, amount, date paid, method, reference (each one
              posts ledger entries; corrections by reversal, as for
              contributions)

---------------------------------------------------------------------
## 4. SCREENS
---------------------------------------------------------------------

1. NEW LOAN (mockup screen 3, corrected as the brief says):
   borrower -> shows shares, the three limits and which one applies,
   any active loan, and anything blocking them (e.g. arrears);
   amount; tenure picker showing the tier, interest, total and
   installment(s) live; guarantor pickers that appear for the amount
   and only offer members who qualify; optional purpose; Disburse
   (with a confirmation step).
2. LOANS LIST: active and repaid loans, outstanding principal, next
   payment due, "due soon" (within 7 days) and "overdue" flags.
3. LOAN PAGE: terms, guarantors, the installment schedule with what's
   paid, repayments, "Record repayment", "Correct" on a repayment, and
   a link to the printable agreement (Q1).
4. EXISTING LOAN: the same form plus the past disbursement date, the
   kind ("checked" / "as agreed") and, for "as agreed", the schedule
   and a note.

DASHBOARD gets: Active loans tile (count + "one payment due Friday"
style flag), and the loans list with outstanding principal.
MEMBER PAGE gets: the active loan tile, and loans they guarantee.
OPENING BALANCES: "loans out" filled in from loans disbursed before the
cut-over.

---------------------------------------------------------------------
## 5. TESTS
---------------------------------------------------------------------

- The 7 test cases in the brief (interest, total, installment).
- Rounding: remainder on the last installment; totals always exact.
- Tier for each tenure; rate stored on the loan is unaffected by later
  setting changes.
- Limit: each of the three rules can be the binding one; the example in
  section 2.
- Eligibility: arrears block; an active loan blocks.
- Guarantors: count by amount (20,000 / 20,001 / 35,000 / 35,001);
  under 20,000 shares refused; arrears refused; already guaranteeing
  refused; self refused; circular refused both ways.
- Disbursement lowers cash but not the pool total.
- Repayments: oldest installment first; interest before principal;
  paying off early includes all the interest; overpayment refused;
  correction reverses the entries.
- Existing loans: "checked" runs the rules, "as agreed" doesn't; a loan
  disbursed before the cut-over feeds the opening balances.

---------------------------------------------------------------------
## 6. NOT IN THIS PHASE (Phase 4)
---------------------------------------------------------------------

Late fees and fee waivers, two-missed acceleration, recovery from
shares, debts to guarantors, pool-loss decisions, extensions,
restructures and schedule changes.

Approve Phase 3?
Answer: approved

# Phase 4 plan: late payments, recovery and group decisions

Status: **draft for review**. Answer the questions in section 1, then approve
at the bottom.

Goal: when a loan payment is missed, the app charges the late fee, makes the
whole balance due after two misses in a row, and lets the recorder recover
what's owed - from the borrower's shares, then the guarantors' shares, then
as a pool loss for the group to decide. Everything the group votes on is
recorded: fee waivers, extensions, restructures, schedule changes and
write-offs.

---------------------------------------------------------------------
## 1. QUESTIONS (answer after each "Answer:")
---------------------------------------------------------------------

Q1. HOW OFTEN IS THE LATE FEE CHARGED? The policy says "per missed month".
    (a) Once per missed installment: the day after its due date, one fee
        (the higher of 500 or 5% of the installment). If it stays unpaid,
        no more fees on it - the next missed installment brings its own.
    (b) Every month an installment stays unpaid: a fee the day after the
        due date, and another each month after that until it's paid.
    For single-payment loans we already agreed (b) (decision R4).
    Suggested: (a) for loans with monthly installments, (b) for single
    payments - so a borrower never pays two fees for the same month.
    Answer: there should be a grace period so 5 days after the due date and the loan remains unpaid, the fee is added. for monthly installments, the fee is added to the installment. for loans less than a month, the fee is added to the total

Q2. AFTER TWO MISSES IN A ROW (whole balance due). The "whole balance" is
    all remaining principal + all remaining interest (the fee is fixed at
    signing) + unpaid late fees. Suggested: no further automatic late
    fees once the whole balance is due - recovery takes over instead
    (otherwise 5% of the whole balance each month piles up fast). OK?
    Answer: ok

Q3. TWO GUARANTORS SHARING A SHORTFALL. Suggested: split equally; if one
    doesn't have enough shares, the other covers the rest of their half
    (up to their own shares). Anything still left is the pool loss. OK?
    Answer: ok. This means their borrowing capacity diminishes, right?

Q4. WHAT A WRITE-OFF DOES. Writing off a pool loss takes that money out of
    the pool for good. Suggested: it comes out of the GROUP money (past
    earnings and interest), not anyone's shares. If the group money isn't
    enough, the app warns you and the group decides separately (e.g.
    reducing everyone's shares equally) - not built in this phase. OK?
    Answer: ok

Q5. EXTENDING A LOAN (group approval). The fee moves to the tier for the
    new total tenure (max 6 months), so the extra interest is the
    difference, e.g. a 3-month 15,000 loan extended to 5 months: interest
    goes from 750 (5%) to 1,200 (8%), so +450. Suggested: what's left to
    pay plus the extra interest is spread equally over the months up to
    the new end date. OK?
    Answer: ok

Q6. RESTRUCTURING. The group agrees a new repayment plan for what's left
    (e.g. after a pool loss). Suggested: no extra interest unless the
    group decides one - the form has an optional "extra charge" field. OK?
    Answer: ok

Q7. RECORDING A GROUP DECISION. Suggested fields: date, what was decided,
    why, and how it was agreed in free text (e.g. "WhatsApp vote, 6 of 7
    in favour" or "Meeting 14 Nov"). OK?
    Answer: I think this might need a whatsapp integrations, right?

---------------------------------------------------------------------
## 2. LATE FEES
---------------------------------------------------------------------

- Fee: the higher of KES 500 or 5% of the missed installment (settings).
  No grace period: charged the day after the due date if the installment
  isn't fully paid.  ---------->>>> grace period of 5 days after the due date
- Charged automatically: the app checks every night, and also when it
  starts and when a loan page opens (so nothing is missed on a laptop
  that was switched off). Checking twice never charges twice.
- Payments clear late fees first, then interest, then principal (L5).
- Fees go into the pool when they're PAID, like interest.
- WAIVING A FEE (R8): from the loan page, with a reason and a group
  decision; the fee stays in the history, marked waived.
  Example: the existing 50,000 loan's schedule was agreed as one payment
  at the end, so no fee arises in the first place.

---------------------------------------------------------------------
## 3. MISSED PAYMENTS AND RECOVERY (policy, decisions R1-R7)
---------------------------------------------------------------------

STATUS
- A loan with an overdue installment shows "Overdue" (red).
- Two missed installments in a row: the loan becomes DEFAULTED and the
  whole balance is due (Q2). Single-payment loans: recovery is available
  once they're one month overdue (R4).

RECOVERY - a clear screen with one confirmation step, not a negotiation:
  The screen shows what's owed (the missed installment + fees, or the
  whole balance if defaulted) and exactly where it will come from:
    1. the borrower's shares
    2. the guarantor(s)' shares (Q3)
    3. whatever is left: a POOL LOSS
  Confirm -> it's recorded in the ledger in that order.
- Taking from shares lowers those shares and pays the loan by the same
  amount, so the pool total doesn't change (R5).
- A recovered installment still counts as MISSED (R3).
- Each guarantor who paid gets a DEBT the borrower owes them (R6). When
  the borrower repays it, the money goes into the pool and restores the
  guarantor's shares.
- A POOL LOSS puts the loan on hold until a group decision: restructure
  or write off (R7).

---------------------------------------------------------------------
## 4. GROUP DECISIONS
---------------------------------------------------------------------

One record type for everything the group votes on (G1): date, what,
why, how agreed (Q7), linked to the loan. Used for:
- waiving a late fee
- extending a loan (Q5) - the new rate is stored on the loan; the old
  one stays in the decision's history
- changing the schedule (e.g. moving payments later) (R9)
- restructuring after a pool loss (Q6) - status RESTRUCTURED
- writing off a pool loss (Q4) - status WRITTEN_OFF
- allowing an MGR opt-out after receiving (M5) - used in Phase 5
A "Decisions" page lists them all.

---------------------------------------------------------------------
## 5. HOW IT'S RECORDED
---------------------------------------------------------------------

NEW LEDGER ENTRY TYPES (brief: every fee and guarantor call is a ledger entry)
  LATE_FEE_CHARGED       fee now owed (not cash yet)
  LATE_FEE_PAID          fee paid                         pool +
  LATE_FEE_WAIVED        fee no longer owed
  RECOVERY_FROM_SHARES   borrower's shares used          shares -, pool -
  GUARANTOR_CALL         a guarantor's shares used       shares -, pool -
  (each of those two is matched by loan repayment entries: pool +)
  GUARANTOR_DEBT_REPAID  borrower repays a guarantor     shares + (guarantor), pool +
  LOAN_WRITTEN_OFF       principal written off            out on loan -

NEW TABLES
  group_decision   date, kind, summary, reason, how agreed, loan, by whom
  late_fee         loan, installment, month, amount, charged on, waived
  guarantor_debt   loan, guarantor, amount owed, amount repaid
  Installments that are replaced by a new schedule are kept and marked
  superseded (nothing is deleted).

---------------------------------------------------------------------
## 6. SCREENS
---------------------------------------------------------------------

LOAN PAGE gets: late fees (with Waive), "Recover" when something is
overdue, debts to guarantors (with "Record repayment to guarantor"),
and buttons for Extend / Change schedule / Restructure / Write off,
each asking for the group decision.
RECOVERY SCREEN: what's owed, where it comes from, confirm.
DECISIONS PAGE: every group decision.
DASHBOARD: overdue loans in red with the amount; a "pool loss awaiting a
decision" notice when there is one.
MEMBER PAGE: shares taken by recovery, and debts owed to/by the member.

---------------------------------------------------------------------
## 7. TESTS
---------------------------------------------------------------------

- Fee amount: 500 minimum, 5% above 10,000 installments; per Q1.
- Charging is automatic and never doubles up.
- Two misses in a row -> DEFAULTED, whole balance due; not after a miss
  followed by a paid installment.
- Recovery: from borrower's shares only; borrower + one guarantor; two
  guarantors split (Q3); pool loss; the pool total unchanged by share
  recovery; recovered installment still counts as missed.
- Guarantor debt created, repaid, guarantor's shares restored.
- Waiver: fee no longer owed, history kept.
- Extension: tier change and extra interest (Q5 example); can't go past
  6 months; needs a decision.
- Restructure / schedule change: old installments kept as superseded,
  totals exact.
- Write-off: pool total drops by the loss; warning if it exceeds group
  money (Q4).

Approve Phase 4?
Answer: approved

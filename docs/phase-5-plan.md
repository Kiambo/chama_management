# Phase 5 plan: the merry-go-round

Status: **draft for review**. Answer the questions in section 1, then approve
at the bottom.

Goal: keep the rotation in the app - who receives each month, how much,
whether everyone has paid in, recording the payout, and moving on to the
next person - including opt-outs, the payout deduction and new rounds.

Already decided (decisions M1-M6, C2-C4, E1):
- Paid on the 10th, once every participating member has paid their MGR
  part in full.
- A recipient who hasn't paid their savings can have it deducted from the
  payout (e.g. 9,000 - 2,000 = 7,000 paid, 2,000 becomes their savings).
- The current round started September 2026 and is Round 5; rounds count
  up. A round has as many months as there are people in the rotation.
- Opting out of MGR in any month loses your turn for that round; you can't
  rejoin until the next round. Once you've received, you can't opt out for
  the rest of the round unless the group decides.
- The pool is 6 x 1,500 until January 2027, then 7 x 1,500. The member
  joining in January is already in this round's order (March 2027).
- The September 2026 payout is entered as history; October is the first
  payout recorded live.

---------------------------------------------------------------------
## 1. QUESTIONS (answer after each "Answer:")
---------------------------------------------------------------------

Q1. THE RECIPIENT'S OWN 1,500. In their month, does the recipient pay their
    own 1,500 into the pool and get it straight back? Suggested: no need -
    the app can count it as paid by netting it from the payout (the
    payout is the others' 1,500s). If they did pay it, it's simply part of
    the pool. OK?
    Answer: yes. usually they pay it and receive it back or they don't send anything and the 2k for savings is deducted from the total they are to receive for MGR. Or they just send the 2k for savings

Q2. WHEN SOMEONE LOSES THEIR TURN (opted out of MGR this round). What
    happens to their month?
    (a) Everyone after them moves up a month; the round ends a month
        early and the next round starts sooner.
    (b) Their month is skipped: no payout that month (the pool is still
        collected? - probably not).
    (c) Something else.
    Suggested: (a).
    Answer: option a

Q3. PAYING OUT EARLY. If everyone has paid in before the 10th, can the
    payout be made early? Suggested: yes - the 10th is the latest it
    should happen, not the earliest.
    Answer: yes. the 10th is just the latest it should happen

Q4. SOMEONE HASN'T PAID THEIR MGR PART. The payout waits (M1). For a
    genuine emergency, suggested: an override that needs a group decision
    (date, how agreed), paying out what's in and recording what's still
    owed by whom. OK?
    Answer: ok

Q5. THE NEXT ROUND. When the last person in Round 5 has been paid, the app
    starts Round 6. Suggested: same order by default, which you can
    rearrange before confirming; members who lost their turn in Round 5
    are back in; members not yet in the MGR are left out. OK?
    Answer: yes

Q6. HOW THE PAYOUT IS SENT. Suggested: record the method (the same
    dropdown) and a reference, like other payments. OK?
    Answer: yes

---------------------------------------------------------------------
## 2. HOW IT WORKS
---------------------------------------------------------------------

ROTATION
- A round is an ordered list of members, one per month from its start
  month. Round 5: September 2026 onwards, in the agreed order (entered on
  a setup screen at go-live).
- "Receiving this month" and "Next up" come straight from the rotation
  and the payouts recorded so far - nothing to "advance" by hand.

THE MONTH'S POOL
- Pool = the MGR parts paid for that month (from the ledger), plus the
  recipient's own part if netted (Q1).
- The payout screen shows: who has paid their MGR part, who hasn't,
  the amount, and any savings deduction for the recipient.

RECORDING A PAYOUT (one confirmation step)
- Gross payout = the month's MGR pool.
- If the recipient's savings for the month aren't paid, you can deduct
  them: that part is recorded as their savings contribution.
- Net paid = gross - deduction, with date, method and reference.
- Ledger: MGR_PAYOUT (the whole pool leaves the MGR side); the deduction
  goes into the MMF as the recipient's savings.
- The MGR side always ends each month at zero - it's a pass-through.
- A mistake is corrected by reversing the payout (latest only), which
  puts the rotation back.

OPT-OUTS (enforced from this phase)
- Opting out of MGR in a month removes that member's turn for the rest
  of the round (Q2).
- After receiving, opting out of MGR is blocked unless you record a group
  decision (MGR opt-out exception).

HISTORY
- The September 2026 payout is entered as history: who and how much.
  It isn't in the ledger, because September's contributions weren't
  either (they're in the opening balances).

---------------------------------------------------------------------
## 3. SCREENS
---------------------------------------------------------------------

1. MERRY-GO-ROUND page: the round (e.g. "Round 5 · month 2 of 7"), the
   order with months, who's received (with amounts), receiving this month,
   next up.
2. PAYOUT screen: this month's pool, who has paid in, the deduction,
   method/reference, confirm.
3. ROUND SETUP: order the members (first round at go-live; each new
   round after the last payout), with the start month.
4. DASHBOARD: the rotation card from the mockup - "Receiving this month:
   name, KES 9,000" and "Next up: name, month".
5. MEMBER PAGE: rotation position, MGR payouts received (count, last),
   and their payouts in the history.

---------------------------------------------------------------------
## 4. TESTS
---------------------------------------------------------------------

- Months and positions from the round order; round length; Round 5 ->
  Round 6.
- The new member: not in the pool before January 2027, in it after;
  receives in March 2027.
- Payout blocked until everyone participating has paid; allowed with a
  group-decision override (Q4).
- Recipient's own part netted (Q1); savings deduction recorded as their
  savings and their month shows Paid.
- MGR side back to zero after each payout.
- Opt-out removes the turn and shifts the order (Q2); blocked after
  receiving unless a group decision is recorded.
- Reversing a payout puts the rotation back.
- History payout recorded without touching the ledger.

Approve Phase 5?
Answer: approved

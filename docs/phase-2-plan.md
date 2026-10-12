# Phase 2 plan: contributions and the ledger

Status: **draft for review**. Answer the questions in section 1, then approve
at the bottom.

Goal: record contributions with the automatic split, see who has paid each
month, and have the MMF pool balance worked out from the ledger only.
The dashboard gets its first real statistics.

---------------------------------------------------------------------
## 1. QUESTIONS (answer after each "Answer:")
---------------------------------------------------------------------

Q1. AGREED BACK-PAYMENTS. The new member pays Sep-Dec savings in January.
    Strictly those are after the 10th, so they'd show "Paid late". Suggested:
    a tick box "agreed back-payment" when recording, which shows the month
    as "Paid" with a note instead. OK?
    Answer: ok

Q2. PAYING MORE THAN IS DUE FOR A MONTH (e.g. 7,000 at once). Since "topping
    up" savings is still for the group to decide, suggested: the app refuses
    more than the month's amount and you record each month separately
    (e.g. two entries of 3,500 for Oct and Nov). OK?
    Answer: ok

Q3. OPENING BALANCES AT 30 SEP 2026. Suggested screen: you enter
    - each member's shares (their savings to date), and
    - the MMF statement balance on 30 Sep.
    The app then works out the "group money" (past fund earnings):
        statement balance + loans out on 30 Sep - total of members' shares
    Do you have each member's share total as at 30 Sep?
    Answer: yes. as well as loans given out

Q4. FUND EARNINGS. You enter them once a month from the statement (month,
    amount, statement reference). OK?
    Answer: ok. can I upload the statement instead?

Q5. PAYMENT METHODS. A drop-down with M-Pesa, PayPal, Bank deposit, Cash,
    and you can type something else. Reference is free text. OK?
    Answer: ok

Q6. TEST DATA vs REAL DATA. Real data goes in at go-live (Phase 7), after
    loans and the MGR exist. Until then, suggested: a "demo" mode with
    made-up members and a few months of payments, in a SEPARATE database
    (chama_demo), so you can see the dashboard working and your real
    database stays clean. Anything you've typed into the real database
    so far would be cleared before go-live. OK?
    Answer: ok

---------------------------------------------------------------------
## 2. HOW THE MONEY IS RECORDED
---------------------------------------------------------------------

LEDGER (the single source of truth)
- Every money movement is one ledger entry: type, amount, member (if any),
  month, date of the payment, when it was recorded and by whom, and a note.
- Entries can never be changed or deleted. This is enforced by the
  DATABASE itself (a Postgres trigger refuses UPDATE and DELETE), not just
  by the app - a good first Postgres feature to learn.
- A mistake is fixed with a REVERSING entry (same amount, opposite sign,
  pointing at the original) and then a new correct entry. History keeps
  all three.

ENTRY TYPES IN THIS PHASE
  OPENING_SHARES      a member's savings at 30 Sep 2026    pool +, shares +
  OPENING_GROUP_FUNDS past earnings at 30 Sep 2026          pool +
  CONTRIBUTION_MMF    the 2,000 savings part                 pool +, shares +
  CONTRIBUTION_MGR    the 1,500 MGR part                     MGR pool +
  FUND_EARNINGS       monthly earnings from the statement    pool +
  (Loans add entries in Phase 3, MGR payouts in Phase 5.)

WORKED OUT FROM THE LEDGER, NEVER TYPED IN
- MMF pool balance = sum of all pool entries.
- A member's shares  = their OPENING_SHARES + CONTRIBUTION_MMF entries.
- This month's MGR pool = this month's CONTRIBUTION_MGR entries.

---------------------------------------------------------------------
## 3. HOW A PAYMENT IS SPLIT
---------------------------------------------------------------------

For a member and a month, the app first works out what is DUE:
  savings due = 2,000, or 0 if they opted out of savings that month
  MGR due     = 1,500, or 0 if they opted out of MGR that month, or the
                month is before their MGR start date
Then the payment fills savings first, then MGR.

  Paid    Savings due  MGR due   -> to savings  to MGR   still owed
  3,500   2,000        1,500        2,000        1,500    0
  2,000   2,000        1,500        2,000        0        1,500 (MGR)
  1,500   2,000        1,500        1,500        0        500 + 1,500
  2,000   2,000        0 (*)        2,000        0        0
  1,500   0 (**)       1,500        0            1,500    0
  (*) not in MGR yet, e.g. the new member's Sep-Dec back-payments
  (**) opted out of savings that month

A member can pay a month in more than one go (e.g. 2,000 now, 1,500 next
week); each payment tops up what's still owed. Amounts and the due day
(10th) are settings, not hard-coded.

---------------------------------------------------------------------
## 4. STATUS PER MEMBER PER MONTH
---------------------------------------------------------------------

  Paid         everything due was paid by the 10th
  Paid late    everything due was paid, the last part after the 10th
  Partly paid  something paid, something still owed
  Due          nothing paid yet, and it's not past the 10th
  Missed       nothing paid, and it's past the 10th
  Opted out    opted out of both savings and MGR that month

Colours as in the brief: green = Paid, amber = Paid late / Partly paid /
Due, red = Missed, grey = Opted out.
Months are tracked from October 2026 (cut-over). Earlier months only show
what was recorded (e.g. back-payments).

---------------------------------------------------------------------
## 5. SCREENS
---------------------------------------------------------------------

1. RECORD CONTRIBUTION (mockup screen 2): member, month, amount (defaults
   to what's still owed), live split preview, payment date (defaults to
   today), method, reference, save.
2. MONTH VIEW: every member's status for a chosen month, what's owed, a
   "Record" button per row, and opt-out per member for that month
   (savings / MGR, with a note).
3. OPENING BALANCES (one-time): members' shares + statement balance at
   30 Sep, showing the worked-out group money before you confirm.
4. FUND EARNINGS: month, amount, statement reference.
5. LEDGER: every entry, newest first, filter by member / type / month,
   with a "Correct" action that reverses an entry (asks for a reason
   and confirmation).
6. MEMBER PAGE (basic): total shares and month-by-month history. The
   polished version matching mockup screen 4 comes in Phase 6.

DASHBOARD gets:
- "N of M contributions are in for October"
- MMF pool balance, and "+X this month"
- MGR pool this month: collected, expected, outstanding
- Goal progress toward 250,000 (setting). Note: until loans exist in
  Phase 3, the "total fund" is just the cash balance.
- This month's status list with a "Record contribution" button

---------------------------------------------------------------------
## 6. TESTS
---------------------------------------------------------------------

- Every row of the split table in section 3.
- Every status in section 4, including paying exactly on the 10th
  (Paid) and on the 11th (Paid late).
- Pool balance and shares come only from ledger entries.
- A reversal brings the balance back, and both entries stay in history.
- The database refuses to UPDATE or DELETE a ledger entry.
- Fund earnings raise the pool but nobody's shares.
- Opening balances: group money worked out correctly; a negative result
  is refused.
- Paying more than is due is refused (if Q2 is agreed).
- Opt-outs reduce what's due and show the right status.
- Dashboard numbers for a known set of payments.

---------------------------------------------------------------------
## 7. NOT IN THIS PHASE
---------------------------------------------------------------------

Loans (Phase 3), MGR payouts and the payout deduction (Phase 5), the rule
blocking an MGR opt-out after receiving (Phase 5, needs rounds), the
polished dashboard and member ledger (Phase 6).

Approve Phase 2?
Answer: approved.

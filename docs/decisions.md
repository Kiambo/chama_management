# Decisions

Answers to the open questions in the build brief (section 9) and gaps found
during planning, recorded 2026-10-09. Where a decision differs from the brief,
it says so.

Real member names, the rotation order and existing loan details are kept in
`docs/private/` (git-ignored), as the brief asks for no real member data in
the repo.

## Stack and setup

| # | Decision | Deviation from brief? |
|---|---|---|
| S1 | Stay on Spring Boot 4.x (the skeleton has 4.1.1). 3.x is past free support. | Yes (brief says 3.x) |
| S2 | PostgreSQL everywhere: a Postgres 16 container (docker-compose, port 5433) on localhost, Neon in production. | No |
| S3 | README.md at the repo root; brief, design PDF, loan policy PDF and this file in `docs/`. | No |
| S4 | Time zone Africa/Nairobi. Foreign payments (PayPal, deposits) are entered in KES as received. | No |
| S5 | A `demo` profile uses a separate `chama_demo` database with made-up data, so the real database stays clean until go-live. | Addition |
| S6 | The database itself refuses UPDATE and DELETE on ledger entries (a Postgres trigger), on top of the app never doing so. | No |
| S7 | A reversing entry carries the original entry's date (the time it was recorded shows when), so balances "as at" a date are as if the mistake never happened. | No |

## Contributions

| # | Decision |
|---|---|
| C1 | Contribution due date is the **10th** of each month. Paid after the 10th = "Paid late". Not paid = "Missed"; becomes "Paid late" when paid. |
| C2 | Savings come first. A payment fills the 2,000 MMF portion before the 1,500 MGR portion. A short payment leaves the MGR portion owed. |
| C3 | Opt-outs are chosen **month by month**. A member can opt out of MGR (savings only) or out of savings (MGR only) for a given month. |
| C4 | Opting out of savings for a month shows as "Opted out", not "Missed", and does not count as money owed. |
| C5 | Cut-over month is **October 2026**. Each member gets an opening shares balance and the pool gets an opening balance as at 30 Sep 2026. Entries are month by month from October. |
| C6 | Fund earnings from the MMF (about 1% a month) are recorded as a "fund earnings" ledger entry from the statement. They belong to the group: they add to the pool, not to anyone's shares. |
| C7 | An **agreed back-payment** (ticked when recording) counts as paid on time, even though it is paid after the due day. |
| C8 | A payment can't be more than is still owed for that month; each month is recorded separately. A month can be paid in several payments. |
| C9 | The statement file (PDF, PNG or JPEG, up to 5 MB) can be attached to fund earnings as evidence. The amount is still typed in. |
| C10 | Opening balances are entered as each member's shares plus the statement balance; the group money is worked out as statement + loans out - shares. They are corrected together (all reversed, then re-entered). |

## MMF pool and loan caps

| # | Decision |
|---|---|
| P1 | For the caps and goal progress, **pool = MMF cash balance + principal still owed on active loans** (the "total chama fund"). |
| P2 | Per-loan cap 50% of pool; total outstanding cap 60% of pool. Configurable. |
| P3 | Goal (250,000, configurable) progress is measured against the pool total (P1). |

## Loans

| # | Decision |
|---|---|
| L1 | Tenure picker offers fixed choices only: 2 weeks, 1 month, 2, 3, 4, 5, 6 months. In-between terms can't be picked (brief Q1). |
| L2 | "Paid up" means no amount currently owed. A past missed month that has since been paid does not block borrowing (brief Q4). |
| L3 | Loan purpose is optional. No cooling-off period (brief Q6). |
| L4 | First installment due 1 month after disbursement, then monthly. A 2-week loan is due on day 14. |
| L5 | Payments clear late fees first, then interest, then principal. Late fees go into the MMF pool, like interest. |
| L6 | "Payment due soon" = due within 7 days. |
| L7 | Loan extensions and restructures get their own screens in v1 (Phase 4). |
| L8 | Loans made before the app can be entered as **existing loans** with their original dates. Two kinds: "recorded as agreed" (rules not re-checked, a custom repayment schedule allowed, with a note) and "checked against the policy". Late-fee and recovery rules apply to both from go-live. |
| L9 | Each loan has a one-page **agreement** (amount, rate, tenure, guarantors, date, schedule). Parties initial it digitally: in v1 the recorder enters each party's initials after they confirm, and the app records who entered them and when. Signing links for members may come after deployment. |
| L10 | A repayment goes to the oldest unpaid installment first; within it, interest before principal. Paying off early means paying all remaining principal and interest. Paying more than the remaining balance is refused. |
| L11 | Only the **most recent** repayment on a loan can be corrected, so the interest/principal split of earlier repayments never changes. |
| L12 | Loan amounts are whole KES. "No arrears" (borrowers and guarantors) means no contribution owed past its due day and no loan installment overdue; opted-out months don't count. |
| L13 | A loan entered by mistake can be **cancelled** (status `CANCELLED`, not in the brief's list) before any repayment; its disbursement is reversed. |
| L14 | Limits are rounded down to whole KES. The per-loan and total caps use the pool total at the moment the loan is checked. |

## Late payment and recovery (follows the loan policy, **deviation from brief**)

The brief only starts recovery after two missed installments. The group was
shown the policy, so the policy applies:

| # | Decision |
|---|---|
| R1 | When an installment is missed, a late fee is added: the higher of 500 KES or 5% of the installment, per missed month, no grace period. |
| R2 | The recorder can then, with one confirmation step, recover the missed installment plus the late fee: first from the borrower's shares, then from the guarantor(s)' shares, then flag the rest as a pool loss. |
| R3 | A recovered installment still counts as **missed**. Two missed in a row makes the whole balance due, and R2 applies to the full balance. |
| R4 | Single-payment loans (1 month or less): a late fee for each month overdue; recovery available once 1 month overdue. |
| R5 | Recovery from shares lowers the member's shares and the loan balance by the same amount. The pool total does not change. Lower shares mean a lower borrowing limit (3 x shares) and possibly losing guarantor eligibility (20,000 minimum). |
| R6 | When a guarantor's shares are used, the borrower owes the guarantor. The app tracks this debt. When the borrower repays it, the money goes into the pool and **restores the guarantor's shares**. |
| R7 | A pool loss is restructured or written off case by case; the app records the group decision (brief Q5). |
| R8 | **Late fees can be forgiven.** The recorder waives a fee with a reason and who approved it. The fee stays in the history; a "fee waived" ledger entry cancels it, so it is no longer owed and no longer counts as pool income. |
| R9 | A repayment schedule can be changed by group decision (recorded with a reason), e.g. moving installments later. |

## Group decisions

| # | Decision |
|---|---|
| G1 | Things the group votes on are recorded as **group decisions** (date, what was decided, reason). Used for: pool-loss handling, loan extensions, restructures and schedule changes, fee waivers, and MGR opt-out exceptions. |

## MGR

| # | Decision |
|---|---|
| M1 | MGR is paid on the 10th, only once every participating member has paid their MGR portion in full. |
| M2 | A recipient who hasn't paid their 2,000 savings can have it deducted from their payout. Example: 9,000 - 2,000 = 7,000 paid out, and 2,000 recorded as the recipient's savings for that month. |
| M3 | The current rotation started September 2026 and is **Round 5**; later rounds count up. A round has as many months as there are members in the rotation. |
| M4 | A member who opts out of MGR in any month **loses their turn for that round** and can't rejoin until the next round starts. |
| M5 | A member who has **already received** their payout this round cannot opt out of MGR for the rest of the round, unless a group decision allows it. |
| M6 | The September 2026 payout is entered as history; October 2026 is the first payout recorded live. |

## Members

| # | Decision |
|---|---|
| E1 | The new member (brief 4.5) has a join date of September 2026 and an MGR start of January 2027. They back-pay 2,000 savings for Sep to Dec 2026. The MGR pool is 6 x 1,500 until then and 7 x 1,500 after. |

## Left to the group (not built)

- Whether a member can "top up" shares with extra savings payments to restore
  borrowing power faster. To be raised at the next meeting.

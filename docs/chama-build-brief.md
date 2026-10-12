# Chama management platform: build brief for Claude Code

Read this whole file before writing code. It is the source of truth for
the domain rules. If the design mockup (`docs/Family_chama_dashboard_design.pdf`)
disagrees with this file, this file wins (see "Known mismatches").

## 1. What we are building

A web app that replaces WhatsApp and Excel for running a family chama
(savings group). It tracks member contributions, the Sunlam MMF savings
pool, internal member loans (with guarantors, interest tiers, and
default handling), and the monthly merry-go-round (MGR) rotation.

- 6 members today, 7 from January 2027 (a new member joins; see section 4.5).
- Currency is KES. All members are family.
- v1 has **one user**: the recorder (the developer, who is one of the
  members). All data is entered manually. No M-Pesa integration.
- Design so that read-only access for the other members can be added
  later without reworking the domain model. Do not hard-code
  "current user" assumptions into loan or ledger logic.

## 2. Stack and decisions already made

| Layer | Choice |
|---|---|
| Language / framework | Java 17, Spring Boot 3.x, single deployable monolith |
| UI | Server-rendered Thymeleaf. No separate SPA, no REST API for a frontend |
| Data access | Spring Data JPA, Flyway migrations |
| Database | PostgreSQL (hosted on Neon; SSL required in the connection string) |
| Hosting | Railway, deployed from GitHub (Dockerfile or Nixpacks) |
| Domain | Registered at Cloudflare, DNS pointed at Railway (CNAME + TXT) |
| Tests | JUnit 5, Testcontainers for Postgres |

Do not propose Vercel, Contabo, or AWS. These were considered and
rejected.

**Auth:** the app holds family money and is on the public internet, so
require login from day one even with one user. Use Spring Security with
form login and BCrypt. Model a `role` on the user (`RECORDER` now,
`MEMBER` read-only later) so access can widen without a rewrite.

## 3. Money and data conventions

- Use `BigDecimal` and `NUMERIC(14,2)`. Never `double` or `float`.
- Interest and installments are rounded to whole KES with
  `RoundingMode.HALF_UP`. When an installment does not divide evenly,
  put the rounding remainder on the **last** installment so the total
  is exact.
- The ledger is **append-only**. No hard deletes of financial records.
  Corrections are made with a reversing entry plus a new entry, so the
  history stays auditable.
- Every contribution, loan, repayment, payout, fee, and guarantor call
  is a ledger entry with a timestamp and the recorder who made it.
- All business thresholds (tier cut-offs, rates, caps, fees) live in
  configuration, not scattered in code. Store the applied rate on the
  `Loan` row at disbursement so later config changes never alter
  loans already out.

## 4. Domain rules

### 4.1 Monthly contribution
- Standard contribution is **3,500 KES per member per month**.
- It splits automatically: **2,000 to the Sunlam MMF** and **1,500 to
  the MGR pool**. Store both portions on the `ContributionRecord`.
- A member's **shares** = the cumulative MMF portion they have
  contributed (the 2,000s), plus any back-dated savings.
- Show paid, paid late, and missed as distinct statuses per member
  per month.

### 4.2 MMF pool balance
Always **computed from the ledger**, never typed in:

`MMF balance = MMF contributions in + loan interest in
 + loan principal repaid - loan principal disbursed`

Loan interest goes back into the pool. It is not paid to members.
The MGR side is a pass-through: collected each month and fully paid
out. It does not sit in the MMF and does not roll over.

### 4.3 Merry-go-round
- An ordered rotation of members. Each month the member whose turn it
  is receives the whole MGR pool for that month (sum of that month's
  1,500 portions).
- On confirming a payout: record an `MGRPayout`, advance the rotation
  to the next member, show "receiving this month" and "next up".
- Round and position are tracked (the design shows "Round 2, month 3
  of 6").

### 4.4 Loans

**Eligibility:** member is paid up (no missed contributions), has no
active loan (one loan at a time), and meets the limits below.

**Borrowing limit:** the lowest of these three:
1. 3 x the member's shares
2. A pool-wide cap per loan: a configurable % of the MMF pool at the
   time of disbursement (default **50%**; the group said 40 to 50%)
3. Remaining headroom under the total-outstanding cap: all active
   loans together must not exceed a configurable % of the pool
   (default **60%**; the group said 50 to 60%)

Show the borrower's effective limit and **which rule is binding**.

**Interest: tiered flat fee.** Calculated once on the original
principal, fixed at signing by the agreed tenure. It is not reducing
balance, not annualized, not charged monthly.

| Tenure | Flat interest |
|---|---|
| Up to 2 weeks (14 days) | 1.5% |
| Up to 1 month | 3% |
| 2 to 3 months | 5% |
| 4 to 6 months | 8% (cap) |

- Maximum tenure is 6 months.
- Repaying early does **not** reduce the fee.
- Extending a loan (needs group approval) moves it to the tier for
  the new total tenure.
- Loans of one month or less are a **single repayment**. From 2
  months, repayment is equal monthly installments of
  (principal + interest) / months.

**Test cases (must pass):**

| Principal | Tenure | Interest | Total | Installment |
|---|---|---|---|---|
| 15,000 | 2 weeks | 225 | 15,225 | single payment |
| 15,000 | 1 month | 450 | 15,450 | single payment |
| 15,000 | 2 months | 750 | 15,750 | 7,875 |
| 15,000 | 3 months | 750 | 15,750 | 5,250 |
| 15,000 | 4 months | 1,200 | 16,200 | 4,050 |
| 15,000 | 6 months | 1,200 | 16,200 | 2,700 |
| 50,000 | 6 months | 4,000 | 54,000 | 9,000 |

**Guarantors:**

| Loan size | Guarantors |
|---|---|
| Up to 20,000 KES | none |
| 20,001 to 35,000 KES | 1 |
| Above 35,000 KES | 2 |

Enforce these as **real validation**, not helper text:
- A guarantor needs at least 20,000 KES in shares and no arrears.
- A guarantor can back only one active loan at a time.
- No circular guarantees: you cannot guarantee someone who is
  currently guaranteeing you or has a loan with you as guarantor.
- A borrower cannot guarantee their own loan.

**Late payment and default:**
- Late fee: the higher of 500 KES or 5% of the missed installment,
  per missed month. No grace period.
- Two consecutive missed installments: the whole outstanding balance
  becomes due immediately.
- Recovery waterfall, applied in this order and recorded as ledger
  entries:
  1. Offset against the borrower's own shares.
  2. Call on the guarantor(s)' shares for the remaining shortfall.
  3. If still short, flag the loan as a pool-wide loss and require a
     recorded group decision: restructure the repayment plan, or
     write off.
- Recovery is a **procedural action** the recorder triggers from a
  clear screen with a confirmation step. It should not feel like a
  negotiation each time.

### 4.5 Members and the January 2027 change
- Members have a join date and a separate MGR start date.
- **A new member** joins in January 2027: they contribute their savings portion
  (2,000 per month) retroactively for September to December 2026,
  and joins the MGR rotation from January 2027 so existing payouts
  are not disrupted. The model must support back-dated contributions
  and a member who is in the savings pool but not yet in the MGR
  rotation.
- Some members live abroad and pay by PayPal or direct deposit to the
  treasurer. So a contribution record has a free-text `method` and
  `reference` field. Do not assume M-Pesa.

## 5. Data model (starting point)

- `Member`: name, join date, MGR start date, active flag
- `ContributionRecord`: member, month, total, `mmf_portion`,
  `mgr_portion`, paid date, status, method, reference
- `Loan`: borrower, principal, tier rate, interest, tenure, disbursed
  date, status (`ACTIVE`, `REPAID`, `DEFAULTED`, `RESTRUCTURED`,
  `WRITTEN_OFF`), purpose (optional text)
- `Guarantee`: loan, guarantor, status
- `Installment`: loan, due date, amount, paid amount, paid date
- `LedgerEntry`: type, amount, member, loan (nullable), timestamp,
  reverses (nullable)
- `MGRCycle` and `MGRPayout`: rotation order, round, position,
  recipient, month, amount, paid date
- `AppUser`: username, password hash, role

## 6. Screens

Reference the mockup PDF for layout and tone. Flat, minimal, warm,
trustworthy. Serif for headings, sans-serif elsewhere. Status colors:
green for paid or on track, amber for needs attention, red for missed
or overdue.

1. **Dashboard**: greeting and "N of M contributions are in", MMF
   pool balance, this month's MGR pool (paid vs outstanding), active
   loans with a due flag, progress toward the 250,000 KES goal, the
   MGR rotation card, the loans list, and a "Record contribution"
   action.
2. **Record contribution**: member, month, total (default 3,500),
   automatic split preview, method and reference, save.
3. **New loan application**: borrower, shares, effective limit with the
   binding rule, existing loans, amount, tenure picker that shows the
   tier, interest and installment preview, guarantor pickers that
   appear and validate by loan size, disburse.
4. **Member ledger**: avatar, join date, rotation position, MGR
   payouts received, total shares, active loan, month-by-month
   contribution table.

## 7. Known mismatches: the mockup is wrong here

- The loan screen has an "Interest rate (% per month)" field showing
  **1.5**. Replace it with the tenure picker and tiered flat fee in
  4.4. There is no per-month rate.
- The mockup shows a borrowing limit of 102,000 KES (3 x 34,000
  shares). That ignores the pool-wide cap. Show the lowest of the
  three limits and name the binding one.
- Names such as "Mwangi family", "Grace", and "Njeri" are placeholders.
  Use real members entered through the UI, with no hard-coded names.
- Active loans in the mockup are 2 at once. That is allowed (different
  borrowers), but each borrower may hold only one.
- The goal tile ("KES 250,000") is the target for moving savings into
  Mansa X. Make the goal amount configurable.

## 8. Build order

Work in this order. Each phase ends with passing tests and a runnable
app.

1. **Foundation**: project skeleton, Flyway, Postgres via
   Testcontainers, login, Member CRUD, Dockerfile.
   *Done when* the app starts, you can log in, and members persist.
2. **Contributions and ledger**: record contributions with automatic
   split, back-dating, status per month, computed MMF balance.
   *Done when* the balance is derived from the ledger only and a
   correction works by reversing entry.
3. **Loans**: limit calculation, tier and installment math (all test
   cases in 4.4), guarantor validation, disbursement, repayments.
   *Done when* every rule in 4.4 has a test.
4. **Default handling**: late fees, acceleration, recovery waterfall,
   pool-loss flag with a recorded group decision.
5. **MGR**: rotation, monthly pool, payout confirmation, advance.
6. **Dashboard and ledger screens**, matching the mockup.
7. **Deploy**: Railway service, Neon database (SSL), environment
   variables, Cloudflare domain, HTTPS check.

## 9. Open decisions: ask before assuming

Do not silently pick an answer. Ask the developer, and record the
answer in `docs/decisions.md`.

1. **Tenures between tiers.** What happens for a term between 1 month
   and 2 months (for example 5 weeks)? Proposed default: round up to
   the 2-month tier.
2. **Contributions not yet in.** If someone has not paid when the MGR
   payout is due, does the recipient get the smaller pool, or does the
   payout wait?
3. **Exact cap percentages.** The group said "40 to 50%" per loan and
   "50 to 60%" total outstanding. Defaults are 50% and 60%. Confirm.
4. **Eligibility of "paid up".** Does one missed month block borrowing
   until it is cleared, or only a current arrears balance?
5. **Pool-loss handling.** Restructure or write-off is decided
   case by case. Confirm that is the intended rule.
6. **Possible extras, not yet agreed with the group:** a one-month
   cooling-off after full repayment, and a purpose-of-loan note.
   Build the purpose field as optional; skip cooling-off unless told.

## 10. Out of scope for v1

- M-Pesa Daraja or Till Number integration (a possible later upgrade
  if the group outgrows manual entry)
- Member login and notifications (SMS or email reminders)
- Multi-chama or multi-tenant support
- Investment tracking for Mansa X or other vehicles; only the goal
  progress tile

## 11. How to work

- Keep commits small and focused. Write a test with each rule.
- Explain any deviation from this brief before making it.
- Do not paste real member data or secrets into the repo. Use
  environment variables for database credentials and the admin seed
  password.

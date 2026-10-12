# Build plan

Approved 2026-10-09. Rules and the reasons behind them are in `decisions.md`.
Each phase ends with passing tests and an app that runs on localhost, and the
work stops after each phase for review.

1. **Foundation** (done): project setup, Postgres (docker-compose locally,
   Testcontainers in tests), Flyway, login (Spring Security, BCrypt,
   `RECORDER` / `MEMBER` roles), member CRUD (no delete, only deactivate),
   page layout, Dockerfile.
2. **Contributions and ledger** (done, see `phase-2-plan.md`): record contributions (savings first, MGR
   second), month-by-month opt-outs, method and reference, back-dating,
   status per member per month (Paid, Paid late, Missed, Partly paid, Opted
   out), opening balances at the cut-over, fund earnings, pool balance from
   the ledger only, corrections by reversing entry.
3. **Loans** (done, see `phase-3-plan.md`): borrowing limit (lowest of three rules, binding rule shown),
   tenure tiers, interest and installment preview, guarantor validation,
   disbursement, repayments (fees, then interest, then principal), existing
   loans ("recorded as agreed" or "checked against the policy"). Tests for
   every rule, including the seven brief test cases.
4. **Late payments and recovery** (done, see `phase-4-plan.md`): late fees, two-in-a-row acceleration,
   recovery with confirmation (borrower's shares, guarantors' shares, pool
   loss), debt owed to guarantors, fee waivers, schedule changes,
   extensions, restructures, group decisions.
5. **Merry-go-round** (done, see `phase-5-plan.md`): rotation and rounds, payout only when all
   participants have paid, deduction of the recipient's missing savings,
   opt-out rules, advance to next.
6. **Dashboard and member ledger screens** (done, see `phase-6-plan.md`; plus treasurer role, profile, settings, statements), matching the mockup.
7. **Go-live on localhost with real data**: members, cut-over balances,
   existing loans and payout history entered through the UI and checked
   against the group's records.
8. **Deploy**: discussed separately first (Railway, Neon, Cloudflare).

## Running locally

```
docker compose up -d          # start Postgres on port 5433
copy .env.example .env        # then fill in the values
.\mvnw spring-boot:run        # start the app on http://localhost:8080
```

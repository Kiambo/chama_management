# Phase 6 plan: polish the dashboard and member pages

Status: **draft for review**. Answer the questions in section 1, then approve
at the bottom.

Goal: make the dashboard and member pages match the mockup (brief section 6),
tidy what's grown over five phases, and close the gaps that matter before
real data goes in (Phase 7) and the app goes online (Phase 8).

Most of the content is already there. This phase is about layout, wording,
phone use, and a few missing basics.

---------------------------------------------------------------------
## 1. QUESTIONS (answer after each "Answer:")
---------------------------------------------------------------------

Q1. CHAMA NAME. The mockup's header reads "Mwangi Family Chama" with a line
    "6 members · est. 2025 · September 2026". Suggested: the name as a
    setting (e.g. "Kiambo Family Chama" - or keep "Family Chama"), and that
    info line under it on the dashboard. What name, and what year was the
    chama established?
    Answer: retain name as Family Chama. Date established is 2024

Q2. YOUR LOGIN AND YOUR MEMBER RECORD. You're a member as well as the
    recorder. Suggested: link your login to your member record, so the
    greeting uses your name and your own page is one click away. It's
    also needed later for read-only member logins. OK?
    Answer: ok

Q3. CHANGE PASSWORD. There's no way to change the password yet - the first
    one came from the .env file. Suggested: a "Change password" page
    (current password, new one twice, at least 12 characters). Needed
    before going online. OK?
    Answer: yes. Add a user profile page so a user can change password, add an email, add a phone number

Q4. DASHBOARD ORDER. Suggested, following the mockup:
      greeting band (with the "N of M contributions are in" line)
      tiles: MMF pool · MGR pool · Active loans · Goal
      merry-go-round rotation card (with Pay out and its status)
      loans list with outstanding principal
      "Record contribution" button
      this month's contribution list (kept - it's useful - but compact,
        at the bottom)
    Notices (pool loss, opening balances missing) stay at the top when
    they apply. The "More is on the way" card goes. OK?
    Answer: ok

Q5. GO-LIVE CHECKLIST. For Phase 7, suggested: until the real data is in, the
    dashboard shows a short checklist in the right order -
      1. members  2. loans out on 30 Sep  3. opening balances
      4. the rotation  5. September's payout (history)
    each ticked automatically when done, then it disappears. OK?
    Answer: ok

Q6. PHONE. Will you mostly use this on a phone, a laptop, or both? Suggested:
    a pass over every page at phone width - tables that scroll sideways
    neatly, menu that wraps cleanly, buttons big enough to tap.
    Answer: both a phone and a laptop

Q7. MEMBER STATEMENT (optional). A "Download statement" button on a member's
    page (a CSV file you can open in Excel or share): their contributions,
    shares, loans and payouts. Useful for answering "how much do I have?"
    questions on WhatsApp. Now, later, or not needed?
    Answer: needed. great idea. 

---------------------------------------------------------------------
## 2. MEMBER PAGE (mockup screen 4)
---------------------------------------------------------------------

- Header: avatar, name, "Member since March 2025 · Rotation position 4 of 7",
  Active badge.
- Three tiles: Total shares (and number of contributions) · Active loan
  (and when the next payment is due) · MGR payouts received (and the last).
- Contribution history table: Month · Total · MMF · MGR · Status, as in the
  mockup (amounts in the serif style, statuses as coloured badges).
- Loan history (from the last round of changes), debts between members.
- An "Edit" link for the member's details.

---------------------------------------------------------------------
## 3. EVERYWHERE
---------------------------------------------------------------------

- One look for page titles, tiles, tables and badges across all pages.
- Money always right-aligned, "KES" only where it helps.
- Empty states that say what to do next ("No loans yet. New loan").
- Accessibility check: contrast of every text colour, visible focus,
  labels on every field, page titles that make sense in the browser tab.
- Phone layout pass (Q6).

---------------------------------------------------------------------
## 4. TESTS
---------------------------------------------------------------------

- Pages render for a member with no data, with a full history, and for a
  brand-new empty database (the go-live starting point).
- Change password: wrong current password refused, too short refused,
  new password works for the next login (Q3).
- Login linked to a member: greeting uses the member's first name (Q2).
- Checklist items tick off as each go-live step is done (Q5).
- Statement download contains the right figures (Q7, if wanted).

Approve Phase 6?
Answer: approved

Additional question: change my role to TREASURER as opposed to RECORDER. Add a configurations page from which I can make loan configurations based on member decisions eg adjusting loan tenures, adding loan tenures. Maybe...after it grows...adding a loan product so that a car loan is priced differently from a regular loan. Adding payment options for future integrations. 

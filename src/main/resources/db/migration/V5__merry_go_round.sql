-- A round of the merry-go-round: members in order, one per month from the start month.
CREATE TABLE mgr_round (
    id           BIGSERIAL    PRIMARY KEY,
    number       INT          NOT NULL UNIQUE CHECK (number >= 1),
    start_month  DATE         NOT NULL CHECK (EXTRACT(DAY FROM start_month) = 1),
    recorded_at  TIMESTAMPTZ  NOT NULL,
    recorded_by  VARCHAR(50)  NOT NULL
);

CREATE TABLE mgr_slot (
    id         BIGSERIAL  PRIMARY KEY,
    round_id   BIGINT     NOT NULL REFERENCES mgr_round (id),
    position   INT        NOT NULL CHECK (position >= 1),
    member_id  BIGINT     NOT NULL REFERENCES member (id),
    CONSTRAINT mgr_slot_position_unique UNIQUE (round_id, position),
    CONSTRAINT mgr_slot_member_unique UNIQUE (round_id, member_id)
);

-- A payout to the month's recipient. Gross = the MGR money paid out for the month; from
-- it, the recipient's own MGR part may be netted and unpaid savings deducted (both are
-- recorded as their contribution). A later top-up pays MGR money that came in after.
CREATE TABLE mgr_payout (
    id                 BIGSERIAL     PRIMARY KEY,
    slot_id            BIGINT        NOT NULL REFERENCES mgr_slot (id),
    member_id          BIGINT        NOT NULL REFERENCES member (id),
    period_month       DATE          NOT NULL CHECK (EXTRACT(DAY FROM period_month) = 1),
    gross              NUMERIC(14,2) NOT NULL CHECK (gross > 0),
    netted_own_mgr     NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (netted_own_mgr >= 0),
    savings_deducted   NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (savings_deducted >= 0),
    paid_date          DATE          NOT NULL,
    method             VARCHAR(50)   NOT NULL,
    reference          VARCHAR(100),
    -- Paid before the app (e.g. September 2026): kept for the record, not in the ledger.
    historical         BOOLEAN       NOT NULL DEFAULT FALSE,
    contribution_id    BIGINT        REFERENCES contribution (id),
    decision_id        BIGINT        REFERENCES group_decision (id),
    recorded_at        TIMESTAMPTZ   NOT NULL,
    recorded_by        VARCHAR(50)   NOT NULL,
    voided_at          TIMESTAMPTZ,
    voided_by          VARCHAR(50),
    void_reason        VARCHAR(500),
    CONSTRAINT mgr_payout_net_positive CHECK (gross - netted_own_mgr - savings_deducted >= 0)
);

CREATE INDEX mgr_payout_month ON mgr_payout (period_month);

ALTER TABLE ledger_entry ADD COLUMN mgr_payout_id BIGINT REFERENCES mgr_payout (id);

ALTER TABLE ledger_entry DROP CONSTRAINT ledger_entry_entry_type_check;
ALTER TABLE ledger_entry ADD CONSTRAINT ledger_entry_entry_type_check CHECK (entry_type IN (
    'OPENING_SHARES', 'OPENING_GROUP_FUNDS',
    'CONTRIBUTION_MMF', 'CONTRIBUTION_MGR', 'FUND_EARNINGS',
    'LOAN_DISBURSED', 'LOAN_PRINCIPAL_REPAID', 'LOAN_INTEREST_PAID',
    'LATE_FEE_CHARGED', 'LATE_FEE_PAID', 'LATE_FEE_WAIVED',
    'RECOVERY_FROM_SHARES', 'GUARANTOR_CALL', 'GUARANTOR_DEBT_REPAID', 'LOAN_WRITTEN_OFF',
    'MGR_PAYOUT'));

ALTER TABLE group_decision DROP CONSTRAINT group_decision_kind_check;
ALTER TABLE group_decision ADD CONSTRAINT group_decision_kind_check CHECK (kind IN (
    'FEE_WAIVER', 'EXTENSION', 'SCHEDULE_CHANGE', 'RESTRUCTURE', 'WRITE_OFF',
    'MGR_OPT_OUT_EXCEPTION', 'MGR_PAYOUT_EXCEPTION', 'OTHER'));

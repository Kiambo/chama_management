-- Month columns (period_month) always hold the first day of the month.

CREATE TABLE contribution (
    id              BIGSERIAL     PRIMARY KEY,
    member_id       BIGINT        NOT NULL REFERENCES member (id),
    period_month    DATE          NOT NULL CHECK (EXTRACT(DAY FROM period_month) = 1),
    amount          NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    mmf_portion     NUMERIC(14,2) NOT NULL CHECK (mmf_portion >= 0),
    mgr_portion     NUMERIC(14,2) NOT NULL CHECK (mgr_portion >= 0),
    paid_date       DATE          NOT NULL,
    method          VARCHAR(50)   NOT NULL,
    reference       VARCHAR(100),
    -- An agreed back-payment counts as on time even though it is paid after the due day.
    agreed_backpay  BOOLEAN       NOT NULL DEFAULT FALSE,
    note            VARCHAR(500),
    recorded_at     TIMESTAMPTZ   NOT NULL,
    recorded_by     VARCHAR(50)   NOT NULL,
    -- A mistaken contribution is voided (its ledger entries are reversed), never deleted.
    voided_at       TIMESTAMPTZ,
    voided_by       VARCHAR(50),
    void_reason     VARCHAR(500),
    CONSTRAINT contribution_split_adds_up CHECK (mmf_portion + mgr_portion = amount),
    CONSTRAINT contribution_void_complete CHECK ((voided_at IS NULL) = (void_reason IS NULL))
);

CREATE INDEX contribution_member_month ON contribution (member_id, period_month);

CREATE TABLE contribution_opt_out (
    id               BIGSERIAL    PRIMARY KEY,
    member_id        BIGINT       NOT NULL REFERENCES member (id),
    period_month     DATE         NOT NULL CHECK (EXTRACT(DAY FROM period_month) = 1),
    opt_out_savings  BOOLEAN      NOT NULL,
    opt_out_mgr      BOOLEAN      NOT NULL,
    note             VARCHAR(500),
    updated_at       TIMESTAMPTZ  NOT NULL,
    updated_by       VARCHAR(50)  NOT NULL,
    CONSTRAINT opt_out_member_month_unique UNIQUE (member_id, period_month)
);

CREATE TABLE fund_earning (
    id                      BIGSERIAL     PRIMARY KEY,
    period_month            DATE          NOT NULL CHECK (EXTRACT(DAY FROM period_month) = 1),
    amount                  NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    statement_reference     VARCHAR(100),
    statement_file_name     VARCHAR(255),
    statement_content_type  VARCHAR(100),
    statement_file          BYTEA,
    recorded_at             TIMESTAMPTZ   NOT NULL,
    recorded_by             VARCHAR(50)   NOT NULL,
    voided_at               TIMESTAMPTZ,
    voided_by               VARCHAR(50),
    void_reason             VARCHAR(500),
    CONSTRAINT fund_earning_void_complete CHECK ((voided_at IS NULL) = (void_reason IS NULL))
);

CREATE TABLE ledger_entry (
    id               BIGSERIAL     PRIMARY KEY,
    entry_type       VARCHAR(40)   NOT NULL CHECK (entry_type IN (
                         'OPENING_SHARES', 'OPENING_GROUP_FUNDS',
                         'CONTRIBUTION_MMF', 'CONTRIBUTION_MGR', 'FUND_EARNINGS')),
    -- Signed: a reversal carries the opposite sign of the entry it reverses.
    amount           NUMERIC(14,2) NOT NULL CHECK (amount <> 0),
    member_id        BIGINT        REFERENCES member (id),
    contribution_id  BIGINT        REFERENCES contribution (id),
    fund_earning_id  BIGINT        REFERENCES fund_earning (id),
    period_month     DATE          CHECK (period_month IS NULL OR EXTRACT(DAY FROM period_month) = 1),
    effective_date   DATE          NOT NULL,
    note             VARCHAR(500),
    -- UNIQUE: an entry can be reversed at most once.
    reverses_id      BIGINT        UNIQUE REFERENCES ledger_entry (id),
    recorded_at      TIMESTAMPTZ   NOT NULL,
    recorded_by      VARCHAR(50)   NOT NULL
);

CREATE INDEX ledger_entry_member ON ledger_entry (member_id);
CREATE INDEX ledger_entry_type_month ON ledger_entry (entry_type, period_month);

-- The ledger is append-only: the database itself refuses to change or remove an entry.
-- Mistakes are corrected with a reversing entry instead.
CREATE FUNCTION ledger_entry_is_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entry is append-only: % is not allowed. Post a reversing entry instead.', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_entry_no_update_or_delete
    BEFORE UPDATE OR DELETE ON ledger_entry
    FOR EACH ROW EXECUTE FUNCTION ledger_entry_is_append_only();

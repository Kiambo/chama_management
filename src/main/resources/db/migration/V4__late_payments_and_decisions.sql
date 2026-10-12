-- Everything the group votes on (decision G1). The evidence file (e.g. a screenshot of
-- a WhatsApp vote) is optional.
CREATE TABLE group_decision (
    id                      BIGSERIAL    PRIMARY KEY,
    decided_on              DATE         NOT NULL,
    kind                    VARCHAR(30)  NOT NULL CHECK (kind IN (
                                'FEE_WAIVER', 'EXTENSION', 'SCHEDULE_CHANGE', 'RESTRUCTURE', 'WRITE_OFF',
                                'MGR_OPT_OUT_EXCEPTION', 'OTHER')),
    summary                 VARCHAR(300) NOT NULL,
    reason                  VARCHAR(1000),
    how_agreed              VARCHAR(300) NOT NULL,
    loan_id                 BIGINT       REFERENCES loan (id),
    evidence_file_name      VARCHAR(255),
    evidence_content_type   VARCHAR(100),
    evidence_file           BYTEA,
    recorded_at             TIMESTAMPTZ  NOT NULL,
    recorded_by             VARCHAR(50)  NOT NULL
);

CREATE INDEX group_decision_loan ON group_decision (loan_id);

-- A new repayment plan for what's left of a loan (extension, schedule change, restructure).
-- The installments it replaces are kept and marked superseded.
CREATE TABLE schedule_change (
    id              BIGSERIAL     PRIMARY KEY,
    loan_id         BIGINT        NOT NULL REFERENCES loan (id),
    decision_id     BIGINT        NOT NULL REFERENCES group_decision (id),
    effective_date  DATE          NOT NULL,
    extra_interest  NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (extra_interest >= 0),
    recorded_at     TIMESTAMPTZ   NOT NULL,
    recorded_by     VARCHAR(50)   NOT NULL
);

ALTER TABLE installment ADD COLUMN schedule_change_id BIGINT REFERENCES schedule_change (id);
ALTER TABLE installment ADD COLUMN superseded_by_change_id BIGINT REFERENCES schedule_change (id);
ALTER TABLE installment DROP CONSTRAINT installment_number_unique;
ALTER TABLE installment ADD CONSTRAINT installment_number_unique UNIQUE NULLS NOT DISTINCT (loan_id, schedule_change_id, number);

-- One late fee per missed installment (Phase 4 plan, Q1).
CREATE TABLE late_fee (
    id                  BIGSERIAL     PRIMARY KEY,
    loan_id             BIGINT        NOT NULL REFERENCES loan (id),
    installment_id      BIGINT        NOT NULL REFERENCES installment (id),
    amount              NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    charged_on          DATE          NOT NULL,
    waived_amount       NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (waived_amount >= 0),
    waived_decision_id  BIGINT        REFERENCES group_decision (id),
    -- Set when a back-dated repayment shows the installment was paid in time after all.
    cancelled           BOOLEAN       NOT NULL DEFAULT FALSE,
    recorded_at         TIMESTAMPTZ   NOT NULL,
    recorded_by         VARCHAR(50)   NOT NULL,
    CONSTRAINT late_fee_waiver_within_amount CHECK (waived_amount <= amount)
);

CREATE UNIQUE INDEX late_fee_one_per_installment ON late_fee (installment_id) WHERE NOT cancelled;

-- Where a payment towards a loan came from: the borrower, or recovery from shares.
ALTER TABLE repayment ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'BORROWER'
    CHECK (source IN ('BORROWER', 'BORROWER_SHARES', 'GUARANTOR_SHARES'));
ALTER TABLE repayment ADD COLUMN guarantor_id BIGINT REFERENCES member (id);

-- What a borrower owes a guarantor whose shares covered their loan (decision R6).
CREATE TABLE guarantor_debt (
    id            BIGSERIAL     PRIMARY KEY,
    loan_id       BIGINT        NOT NULL REFERENCES loan (id),
    guarantor_id  BIGINT        NOT NULL REFERENCES member (id),
    repayment_id  BIGINT        NOT NULL REFERENCES repayment (id),
    amount        NUMERIC(14,2) NOT NULL CHECK (amount > 0)
);

CREATE TABLE guarantor_debt_payment (
    id           BIGSERIAL     PRIMARY KEY,
    debt_id      BIGINT        NOT NULL REFERENCES guarantor_debt (id),
    amount       NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    paid_date    DATE          NOT NULL,
    method       VARCHAR(50)   NOT NULL,
    reference    VARCHAR(100),
    recorded_at  TIMESTAMPTZ   NOT NULL,
    recorded_by  VARCHAR(50)   NOT NULL,
    voided_at    TIMESTAMPTZ,
    voided_by    VARCHAR(50),
    void_reason  VARCHAR(500)
);

ALTER TABLE loan ADD COLUMN defaulted_on DATE;
-- Recovery left a shortfall: the loan waits for the group to restructure or write off.
ALTER TABLE loan ADD COLUMN awaiting_decision BOOLEAN NOT NULL DEFAULT FALSE;

-- One open loan per member: active, defaulted or restructured.
DROP INDEX loan_one_active_per_borrower;
CREATE UNIQUE INDEX loan_one_open_per_borrower ON loan (borrower_id)
    WHERE status IN ('ACTIVE', 'DEFAULTED', 'RESTRUCTURED');

ALTER TABLE ledger_entry ADD COLUMN late_fee_id BIGINT REFERENCES late_fee (id);
ALTER TABLE ledger_entry ADD COLUMN debt_payment_id BIGINT REFERENCES guarantor_debt_payment (id);

ALTER TABLE ledger_entry DROP CONSTRAINT ledger_entry_entry_type_check;
ALTER TABLE ledger_entry ADD CONSTRAINT ledger_entry_entry_type_check CHECK (entry_type IN (
    'OPENING_SHARES', 'OPENING_GROUP_FUNDS',
    'CONTRIBUTION_MMF', 'CONTRIBUTION_MGR', 'FUND_EARNINGS',
    'LOAN_DISBURSED', 'LOAN_PRINCIPAL_REPAID', 'LOAN_INTEREST_PAID',
    'LATE_FEE_CHARGED', 'LATE_FEE_PAID', 'LATE_FEE_WAIVED',
    'RECOVERY_FROM_SHARES', 'GUARANTOR_CALL', 'GUARANTOR_DEBT_REPAID', 'LOAN_WRITTEN_OFF'));

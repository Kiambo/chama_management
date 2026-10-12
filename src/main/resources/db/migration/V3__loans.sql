CREATE TABLE loan (
    id                      BIGSERIAL     PRIMARY KEY,
    borrower_id             BIGINT        NOT NULL REFERENCES member (id),
    -- Whole shillings only (Phase 3 plan, Q5).
    principal               NUMERIC(14,2) NOT NULL CHECK (principal > 0 AND principal = TRUNC(principal)),
    tenure                  VARCHAR(20)   NOT NULL CHECK (tenure IN (
                                'TWO_WEEKS', 'ONE_MONTH', 'TWO_MONTHS', 'THREE_MONTHS',
                                'FOUR_MONTHS', 'FIVE_MONTHS', 'SIX_MONTHS')),
    -- The rate in force when the loan was disbursed, so later setting changes never alter it.
    rate_percent            NUMERIC(5,2)  NOT NULL CHECK (rate_percent >= 0),
    interest                NUMERIC(14,2) NOT NULL CHECK (interest >= 0),
    total                   NUMERIC(14,2) NOT NULL,
    disbursed_date          DATE          NOT NULL,
    status                  VARCHAR(20)   NOT NULL CHECK (status IN (
                                'ACTIVE', 'REPAID', 'DEFAULTED', 'RESTRUCTURED', 'WRITTEN_OFF', 'CANCELLED')),
    kind                    VARCHAR(30)   NOT NULL CHECK (kind IN ('NEW', 'EXISTING_CHECKED', 'EXISTING_AS_AGREED')),
    purpose                 VARCHAR(300),
    note                    VARCHAR(500),
    disbursement_method     VARCHAR(50),
    disbursement_reference  VARCHAR(100),
    recorded_at             TIMESTAMPTZ   NOT NULL,
    recorded_by             VARCHAR(50)   NOT NULL,
    cancel_reason           VARCHAR(500),
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT loan_total_adds_up CHECK (total = principal + interest)
);

CREATE INDEX loan_borrower ON loan (borrower_id);
-- One active loan per member, enforced by the database as well as the app.
CREATE UNIQUE INDEX loan_one_active_per_borrower ON loan (borrower_id) WHERE status = 'ACTIVE';

CREATE TABLE guarantee (
    id            BIGSERIAL   PRIMARY KEY,
    loan_id       BIGINT      NOT NULL REFERENCES loan (id),
    guarantor_id  BIGINT      NOT NULL REFERENCES member (id),
    CONSTRAINT guarantee_loan_guarantor_unique UNIQUE (loan_id, guarantor_id)
);

CREATE TABLE installment (
    id              BIGSERIAL     PRIMARY KEY,
    loan_id         BIGINT        NOT NULL REFERENCES loan (id),
    number          INT           NOT NULL CHECK (number >= 1),
    due_date        DATE          NOT NULL,
    amount          NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    interest_part   NUMERIC(14,2) NOT NULL CHECK (interest_part >= 0),
    principal_part  NUMERIC(14,2) NOT NULL CHECK (principal_part >= 0),
    CONSTRAINT installment_number_unique UNIQUE (loan_id, number),
    CONSTRAINT installment_parts_add_up CHECK (interest_part + principal_part = amount)
);

CREATE TABLE repayment (
    id           BIGSERIAL     PRIMARY KEY,
    loan_id      BIGINT        NOT NULL REFERENCES loan (id),
    amount       NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    paid_date    DATE          NOT NULL,
    method       VARCHAR(50)   NOT NULL,
    reference    VARCHAR(100),
    recorded_at  TIMESTAMPTZ   NOT NULL,
    recorded_by  VARCHAR(50)   NOT NULL,
    voided_at    TIMESTAMPTZ,
    voided_by    VARCHAR(50),
    void_reason  VARCHAR(500),
    CONSTRAINT repayment_void_complete CHECK ((voided_at IS NULL) = (void_reason IS NULL))
);

CREATE INDEX repayment_loan ON repayment (loan_id);

-- Initials on the digital loan agreement. In v1 the recorder enters them after each
-- party has confirmed; recorded_by says who entered them.
CREATE TABLE loan_signature (
    id           BIGSERIAL    PRIMARY KEY,
    loan_id      BIGINT       NOT NULL REFERENCES loan (id),
    role         VARCHAR(20)  NOT NULL CHECK (role IN ('BORROWER', 'GUARANTOR', 'TREASURER')),
    member_id    BIGINT       REFERENCES member (id),
    name         VARCHAR(100) NOT NULL,
    initials     VARCHAR(10)  NOT NULL,
    signed_date  DATE         NOT NULL,
    recorded_at  TIMESTAMPTZ  NOT NULL,
    recorded_by  VARCHAR(50)  NOT NULL
);

CREATE UNIQUE INDEX loan_signature_one_per_party ON loan_signature (loan_id, role, COALESCE(member_id, 0));

ALTER TABLE ledger_entry ADD COLUMN loan_id BIGINT REFERENCES loan (id);
ALTER TABLE ledger_entry ADD COLUMN repayment_id BIGINT REFERENCES repayment (id);
CREATE INDEX ledger_entry_loan ON ledger_entry (loan_id);

ALTER TABLE ledger_entry DROP CONSTRAINT ledger_entry_entry_type_check;
ALTER TABLE ledger_entry ADD CONSTRAINT ledger_entry_entry_type_check CHECK (entry_type IN (
    'OPENING_SHARES', 'OPENING_GROUP_FUNDS',
    'CONTRIBUTION_MMF', 'CONTRIBUTION_MGR', 'FUND_EARNINGS',
    'LOAN_DISBURSED', 'LOAN_PRINCIPAL_REPAID', 'LOAN_INTEREST_PAID'));

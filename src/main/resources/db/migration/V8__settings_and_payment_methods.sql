-- Business settings changed in the app by group decision. Each value applies from its
-- effective date; the defaults in application.properties apply before any change. A value
-- is looked up "as at" the date it matters (e.g. a month's contribution amount as at the
-- 1st of that month), so a change never rewrites the past.
CREATE TABLE setting_value (
    id              BIGSERIAL     PRIMARY KEY,
    setting_key     VARCHAR(60)   NOT NULL,
    value           NUMERIC(14,4) NOT NULL,
    effective_from  DATE          NOT NULL,
    decision_id     BIGINT        NOT NULL REFERENCES group_decision (id),
    recorded_at     TIMESTAMPTZ   NOT NULL,
    recorded_by     VARCHAR(50)   NOT NULL
);

CREATE INDEX setting_value_key_date ON setting_value (setting_key, effective_from);

-- The payment methods offered in the dropdowns. "Other..." is always offered as well.
CREATE TABLE payment_method (
    id        BIGSERIAL    PRIMARY KEY,
    name      VARCHAR(50)  NOT NULL,
    active    BOOLEAN      NOT NULL DEFAULT TRUE,
    position  INT          NOT NULL
);

CREATE UNIQUE INDEX payment_method_name_unique ON payment_method (LOWER(name));

INSERT INTO payment_method (name, position) VALUES
    ('M-Pesa', 1), ('PayPal', 2), ('Bank deposit', 3), ('Bank transfer', 4), ('Cash', 5);

ALTER TABLE group_decision DROP CONSTRAINT group_decision_kind_check;
ALTER TABLE group_decision ADD CONSTRAINT group_decision_kind_check CHECK (kind IN (
    'FEE_WAIVER', 'EXTENSION', 'SCHEDULE_CHANGE', 'RESTRUCTURE', 'WRITE_OFF',
    'MGR_OPT_OUT_EXCEPTION', 'MGR_PAYOUT_EXCEPTION', 'SETTING_CHANGE', 'OTHER'));

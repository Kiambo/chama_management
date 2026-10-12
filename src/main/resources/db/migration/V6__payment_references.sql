-- One register of payment references (M-Pesa codes, PayPal IDs, bank references) across
-- every kind of payment, so the same reference can't be entered twice - for the same
-- payment or a different one. Compared without case or spaces. A corrected (reversed)
-- payment releases its reference, so the correct payment can be entered with it again.
CREATE TABLE payment_reference (
    id             BIGSERIAL     PRIMARY KEY,
    reference_key  VARCHAR(100)  NOT NULL,
    reference      VARCHAR(100)  NOT NULL,
    kind           VARCHAR(30)   NOT NULL CHECK (kind IN (
                       'CONTRIBUTION', 'LOAN_DISBURSEMENT', 'LOAN_REPAYMENT', 'GUARANTOR_REPAYMENT', 'MGR_PAYOUT')),
    record_id      BIGINT        NOT NULL,
    description    VARCHAR(300)  NOT NULL,
    recorded_at    TIMESTAMPTZ   NOT NULL,
    released_at    TIMESTAMPTZ
);

CREATE UNIQUE INDEX payment_reference_in_use ON payment_reference (reference_key) WHERE released_at IS NULL;
CREATE INDEX payment_reference_record ON payment_reference (kind, record_id);

-- References already entered. If any already clash, the earliest keeps it.
INSERT INTO payment_reference (reference_key, reference, kind, record_id, description, recorded_at)
SELECT DISTINCT ON (reference_key) reference_key, reference, kind, record_id, description, recorded_at
FROM (
    SELECT UPPER(REGEXP_REPLACE(c.reference, '\s', '', 'g')) AS reference_key, c.reference, 'CONTRIBUTION' AS kind,
           c.id AS record_id, 'Contribution from ' || m.full_name AS description, c.recorded_at
    FROM contribution c JOIN member m ON m.id = c.member_id
    WHERE c.reference IS NOT NULL AND c.voided_at IS NULL
    UNION ALL
    SELECT UPPER(REGEXP_REPLACE(l.disbursement_reference, '\s', '', 'g')), l.disbursement_reference, 'LOAN_DISBURSEMENT',
           l.id, 'Loan to ' || m.full_name, l.recorded_at
    FROM loan l JOIN member m ON m.id = l.borrower_id
    WHERE l.disbursement_reference IS NOT NULL AND l.status <> 'CANCELLED'
    UNION ALL
    SELECT UPPER(REGEXP_REPLACE(r.reference, '\s', '', 'g')), r.reference, 'LOAN_REPAYMENT',
           r.id, 'Loan repayment from ' || m.full_name, r.recorded_at
    FROM repayment r JOIN loan l ON l.id = r.loan_id JOIN member m ON m.id = l.borrower_id
    WHERE r.reference IS NOT NULL AND r.voided_at IS NULL
    UNION ALL
    SELECT UPPER(REGEXP_REPLACE(p.reference, '\s', '', 'g')), p.reference, 'GUARANTOR_REPAYMENT',
           p.id, 'Repayment to a guarantor', p.recorded_at
    FROM guarantor_debt_payment p
    WHERE p.reference IS NOT NULL AND p.voided_at IS NULL
    UNION ALL
    SELECT UPPER(REGEXP_REPLACE(o.reference, '\s', '', 'g')), o.reference, 'MGR_PAYOUT',
           o.id, 'MGR payout to ' || m.full_name, o.recorded_at
    FROM mgr_payout o JOIN member m ON m.id = o.member_id
    WHERE o.reference IS NOT NULL AND o.voided_at IS NULL
) existing
WHERE reference_key <> ''
ORDER BY reference_key, recorded_at;

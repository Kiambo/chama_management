CREATE TABLE member (
    id              BIGSERIAL    PRIMARY KEY,
    full_name       VARCHAR(100) NOT NULL,
    join_date       DATE         NOT NULL,
    -- Null means the member is in the savings pool but not (yet) in the MGR rotation.
    mgr_start_date  DATE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(50)  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      VARCHAR(50)  NOT NULL,
    CONSTRAINT member_mgr_start_after_join CHECK (mgr_start_date IS NULL OR mgr_start_date >= join_date)
);

CREATE UNIQUE INDEX member_full_name_unique ON member (LOWER(full_name));

CREATE TABLE app_user (
    id             BIGSERIAL    PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    role           VARCHAR(20)  NOT NULL CHECK (role IN ('RECORDER', 'MEMBER')),
    -- The member this login belongs to, if any (used for the greeting, and for
    -- read-only member access later).
    member_id      BIGINT       REFERENCES member (id),
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

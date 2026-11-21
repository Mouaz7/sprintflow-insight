CREATE TABLE personal_access_token (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label varchar(80) NOT NULL,
    token_digest char(64) NOT NULL UNIQUE,
    scopes text[] NOT NULL CHECK (cardinality(scopes) BETWEEN 1 AND 3
      AND scopes <@ ARRAY['PROJECT_READ','TASK_READ','TASK_WRITE']::text[]),
    mfa_verified boolean NOT NULL,
    issued_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    expires_at timestamptz NOT NULL,
    last_used_at timestamptz,
    CHECK (expires_at > issued_at)
);
CREATE INDEX personal_access_token_user_idx ON personal_access_token(user_id, id DESC);

CREATE TABLE outbound_webhook (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id bigint NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    target_url varchar(1024) NOT NULL,
    secret_ciphertext text NOT NULL,
    event_types text[] NOT NULL CHECK (cardinality(event_types) BETWEEN 1 AND 4
      AND event_types <@ ARRAY['task.created','task.updated','task.moved','task.deleted']::text[]),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX outbound_webhook_project_idx ON outbound_webhook(project_id, created_at DESC);

CREATE TABLE outbound_webhook_delivery (
    id uuid PRIMARY KEY,
    webhook_id bigint NOT NULL REFERENCES outbound_webhook(id) ON DELETE CASCADE,
    event_id uuid NOT NULL,
    event_type varchar(20) NOT NULL,
    payload text NOT NULL CHECK (octet_length(payload) <= 4096),
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    next_attempt_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    claim_token uuid,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    outcome varchar(12) NOT NULL DEFAULT 'PENDING'
      CHECK (outcome IN ('PENDING','DELIVERED','FAILED')),
    UNIQUE (webhook_id, event_id)
);
CREATE INDEX outbound_delivery_ready_idx ON outbound_webhook_delivery(next_attempt_at, id)
    WHERE outcome='PENDING';
CREATE INDEX outbound_delivery_webhook_idx ON outbound_webhook_delivery(webhook_id, created_at DESC);

CREATE TABLE outbound_webhook_attempt (
    delivery_id uuid NOT NULL REFERENCES outbound_webhook_delivery(id) ON DELETE CASCADE,
    attempt integer NOT NULL CHECK (attempt BETWEEN 1 AND 5),
    status integer CHECK (status BETWEEN 100 AND 599),
    result varchar(16) NOT NULL CHECK (result IN ('DELIVERED','REJECTED','UNREACHABLE','TIMED_OUT','INTERRUPTED')),
    attempted_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (delivery_id, attempt)
);

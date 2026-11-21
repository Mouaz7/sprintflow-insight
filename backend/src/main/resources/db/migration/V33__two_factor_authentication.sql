ALTER TABLE organization ADD COLUMN require_mfa boolean NOT NULL DEFAULT false;

CREATE TABLE account_mfa (
    user_id bigint PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    secret_ciphertext text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    enabled_at timestamptz,
    last_step bigint CHECK (last_step >= 0),
    failures integer NOT NULL DEFAULT 0 CHECK (failures BETWEEN 0 AND 10),
    last_failure timestamptz,
    CHECK (enabled_at IS NOT NULL OR last_step IS NULL)
);

CREATE TABLE account_mfa_recovery (
    user_id bigint NOT NULL REFERENCES account_mfa(user_id) ON DELETE CASCADE,
    digest character(64) NOT NULL CHECK (digest ~ '^[a-f0-9]{64}$'),
    PRIMARY KEY (user_id, digest)
);

CREATE TABLE account_mfa_challenge (
    digest character(64) PRIMARY KEY CHECK (digest ~ '^[a-f0-9]{64}$'),
    user_id bigint NOT NULL REFERENCES account_mfa(user_id) ON DELETE CASCADE,
    credential_digest character(64) NOT NULL CHECK (credential_digest ~ '^[a-f0-9]{64}$'),
    expires_at timestamptz NOT NULL,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5)
);
CREATE INDEX account_mfa_challenge_expiry_idx ON account_mfa_challenge(expires_at);
CREATE INDEX account_mfa_challenge_user_idx ON account_mfa_challenge(user_id);

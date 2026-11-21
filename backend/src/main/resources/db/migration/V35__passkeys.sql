-- Table names and fields match Spring Security's JDBC WebAuthn repositories.
CREATE TABLE user_entities (
    id varchar(100) PRIMARY KEY,
    name varchar(20) NOT NULL UNIQUE,
    display_name varchar(255) NOT NULL,
    owner_id bigint GENERATED ALWAYS AS (name::bigint) STORED
        REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE user_credentials (
    credential_id varchar(1400) PRIMARY KEY,
    user_entity_user_id varchar(100) NOT NULL REFERENCES user_entities(id) ON DELETE CASCADE,
    public_key bytea NOT NULL,
    signature_count bigint NOT NULL CHECK (signature_count >= 0),
    uv_initialized boolean NOT NULL,
    backup_eligible boolean NOT NULL,
    authenticator_transports text,
    public_key_credential_type varchar(32) NOT NULL,
    backup_state boolean NOT NULL,
    attestation_object bytea,
    attestation_client_data_json bytea,
    created timestamptz NOT NULL,
    last_used timestamptz,
    label varchar(80) NOT NULL
);
CREATE INDEX webauthn_user_credentials_idx ON user_credentials(user_entity_user_id);

CREATE TABLE passkey_challenge (
    session_digest char(64) NOT NULL,
    purpose varchar(10) NOT NULL CHECK (purpose IN ('REGISTER', 'LOGIN')),
    challenge_digest char(64) NOT NULL,
    expires_at timestamptz NOT NULL,
    PRIMARY KEY (session_digest, purpose)
);
CREATE INDEX passkey_challenge_expiry_idx ON passkey_challenge(expires_at);

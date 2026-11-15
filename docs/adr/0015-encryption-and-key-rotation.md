# ADR 0015: Application-level encryption with versioned keys

Status: accepted for B0. Later phases (webhook secrets, integration tokens, import credentials) store their secrets through this service.

## Context

Coming features must keep third-party secrets that the server itself has to read again, so hashing (as used for bearer tokens and passwords) does not fit. Disk or volume encryption does not protect against a leaked database dump or backup. Keys must also be replaceable without downtime or data loss, which the OWASP ASVS 5.0 level 2 cryptography chapter (V11) expects.

## Decision

`common.crypto.EncryptionService` encrypts strings with **AES-256-GCM** from the JDK provider.

- **Envelope.** A value is stored as text: `sf1:<keyId>:<base64url(iv | ciphertext | tag)>`. The IV is 96 random bits from `SecureRandom` for every value, and the tag is 128 bits. Random IVs stay far below GCM's collision bound for the volumes this application stores.
- **Associated data.** `sf1:<keyId>:<context>` is authenticated. The context names the field (`table.column`). A changed byte, a relabelled key id or a value copied into another field all fail with `EncryptionException` instead of decrypting to something else. The context does not bind a row id, because callers usually encrypt before an identity id exists. Swapping values between rows of the same column requires write access to the database and is out of scope.
- **Key ids.** The id is the first 4 bytes (8 hex characters) of `SHA-256("sprintflow-encryption-key-id:" ‖ key)`. Operators never assign ids. Each ciphertext names the exact key that wrote it, and a configured key cannot be mistaken for another one. Duplicate keys are rejected at startup.
- **Configuration.** `SPRINTFLOW_ENCRYPTION_KEY` holds Base64 of exactly 32 bytes. `SPRINTFLOW_ENCRYPTION_PREVIOUS_KEYS` holds retired keys, comma separated. The bean is created eagerly, and startup fails with an operator hint if the key is missing, not Base64 or the wrong length. Error messages never contain key material. There is no fallback key in production or development code. `start.sh`, `scripts/start-docker.ps1` and `scripts/start-native.ps1` generate the key into `.env` once, as they already do for the database password, and never replace it. Compose refuses to start without it. Tests register a per-JVM random key through a test-only `EnvironmentPostProcessor`.
- **Rotation.** Features register each encrypted column as an `EncryptedColumn` bean (table, numeric id column, value column, context). `ReEncryptionJob` runs hourly (`app.encryption.reencrypt-cron`). In batches of `app.encryption.reencrypt-batch-size` (200), each in its own transaction with `SELECT … FOR UPDATE SKIP LOCKED`, it rewrites every value whose prefix is not `sf1:<currentKeyId>:`. The job is idempotent and safe on several instances. A value no configured key can read is counted, logged with table, column and row id only, and skipped, so the job never loops on it.

### Rotation procedure

1. Generate a key: `head -c 32 /dev/urandom | base64`.
2. Set it as `SPRINTFLOW_ENCRYPTION_KEY` and move the old key to `SPRINTFLOW_ENCRYPTION_PREVIOUS_KEYS`. Restart. New writes use the new key, and old values still decrypt.
3. Wait for the next hourly run, or trigger `ReEncryptionJob.run()`. `ReEncryptionJob.pending()` must report 0. A non-zero failure count means values were written with a key that is no longer configured.
4. Remove the old key from `SPRINTFLOW_ENCRYPTION_PREVIOUS_KEYS` and restart.

Losing every copy of the current key makes encrypted values unreadable by design. Back up `.env` (or the secret store used in deployment, phase C6) separately from database backups.

## Consequences

- Secrets at rest are protected against database and backup leaks. They are not protected against a compromised application host, which holds the key.
- Encrypted columns cannot be searched or indexed by value. Features look secrets up by their owning row.
- The `crypto` named interface of `common` is the only way to encrypt. Modules depend on it explicitly (`common :: crypto`).
- Key management in an external KMS (envelope encryption with a wrapped data key) can replace the environment variable later without changing the stored format, because the key id already travels with every value.

Verification: `EncryptionServiceTest`, `KeyringTest` and the jqwik `EncryptionProperties` cover round trips, fresh IVs, single-bit tamper detection, context binding and rotation. `ReEncryptionJobTest` covers batching and failure accounting. `ReEncryptionIntegrationTest` runs the rotation SQL on PostgreSQL.

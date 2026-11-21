# ADR 0035 — Authenticator codes and organization sign-in requirements

Status accepted.

Use 160-bit authenticator secrets and the six-digit, 30-second HMAC-SHA-1 profile. Servers tolerate one adjacent time step on either side of the current step. The greatest accepted step is stored with the encrypted secret and checked under a row lock. A time step can be accepted once. [RFC 6238](https://www.rfc-editor.org/rfc/rfc6238) and [RFC 4226](https://www.rfc-editor.org/rfc/rfc4226).

The existing encryption keyring protects the authenticator secret. The rotation job registers its column. A pending setup expires after ten minutes. The browser builds the provisioning QR code from the setup URI with `qrcode-generator`. After confirming the password and authenticator code, the server returns ten independently generated recovery codes with 80 bits of entropy each. The database stores their SHA-256 digests and consumes a recovery code in the authentication transaction.

Password and Google sign-in return a pending session when the account has TOTP. The session has a five-minute challenge and receives a fresh CSRF token. The app establishes its authenticated session only after the second factor, and rotates its ID. A challenge is tied to the password digest observed at sign-in so changing the password invalidates it. Verification counts five attempts per challenge and ten consecutive failures per account. A ten-minute quiet period resets the account limit. The existing IP rate limiter also applies. Enrollment and deactivation revoke other sessions.

A verified organization owner can require two-factor authentication. The policy covers organization members and members of its projects across workspace APIs and STOMP. Accounts requiring enrollment can still sign in to profile settings and export their own account data. The workspace opens after enrollment. Existing realtime connections are rechecked when the requirement changes and when they subscribe or send. An account that still belongs to a requiring organization keeps two-factor authentication enabled.

The service never sends the QR URI to a remote generator. Recovery codes and setup secrets are returned with no-store and request DTOs redact these values from text representations. [QR encoder source](https://github.com/kazuhikoarase/qrcode-generator).

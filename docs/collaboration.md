# Shared task and retrospective text

Open **Co-edit description** on a task or **Co-edit note** on a retrospective entry. An editor can start a shared room. The shared editor shows whether updates have synced and whose cursor is present. An offline tab keeps local changes in memory and resends them with the same identifiers on reconnect.

## Protocol

The session uses the existing same-origin WebSocket at `/ws` and includes its CSRF header in STOMP CONNECT. Subscribe to `/topic/projects/{projectId}/collaboration/{target}/{id}/updates` and `/awareness` on the same room path. Send to `/app/projects/{projectId}/collaboration/{target}/{id}/updates` or `/awareness`. Valid targets are `TASK` and `RETROSPECTIVE`.

- Updates use STOMP `application/octet-stream` with an explicit byte count. A SEND body contains 16 UUID bytes followed by up to 12 KiB of Yjs bytes. A broker response prefixes the same body with an eight-byte big-endian revision. A duplicate receives its original revision.
- Awareness uses JSON with nullable base64 `anchor` and `head` values encoded as Yjs relative positions, each at most 256 bytes. The server supplies the account, email, session and timestamp. Clients refresh active cursors approximately every ten seconds and expire them after 30 seconds. Awareness is in memory.
- The [REST contract](openapi.yaml) describes item metadata, room initialization, incremental recovery and projection.

## Threat review

| STRIDE | Control |
| --- | --- |
| Spoofing | A session cookie and STOMP CSRF token bind the connection to a user. The sender cannot select the user identity in awareness. |
| Tampering | Project and item scope checks apply to writes. PostgreSQL checks resource ownership, locks revisions and rejects a UUID reused with a different hash. |
| Repudiation | The application logs its normal task and workspace change events on projection. The revision and receipt records identify the stored update. |
| Information disclosure | Only project readers can subscribe. Project membership changes revoke affected subscriptions. Snapshot reads apply the same scope and gzip data is decoded only inside the client. |
| Denial of service | The project connection and subscription limits apply. STOMP frames, presence fields, update bytes and decompressed document history have bounds. |
| Elevation of privilege | Viewer and archived roles are read-only. Task and note access is rechecked at write time, and ordinary note contributors may only edit their own notes. |

The broker forwards opaque Yjs updates from authorized editors and the display projection is supplied by those editors. An editor's access therefore carries the same trust as write access to the underlying text. A room stops accepting new updates when its storage limit is reached. Keep a copy of unacknowledged text before closing a tab that cannot reconnect. See [ADR 0033](adr/0033-shared-text-crdt.md) and the [verification results](evaluation/collaboration.md).

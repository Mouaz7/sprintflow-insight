# ADR 0033 — Collaborative task and retrospective text

Status accepted.

Tasks and retrospective notes can have several writers on slow or temporarily unavailable connections. Each browser needs to respond to local edits immediately. Reconnected clients must be able to retransmit an update without applying it twice.

| Approach | Editing over a slow or interrupted connection | Server responsibilities |
| --- | --- | --- |
| Operational Transformation | Clients retain the operations after their last agreed revision. Concurrent position-based edits require transformation against intervening history. | Order operations and apply the same transform rules used by clients. |
| Yjs CRDT | Clients apply local updates immediately. Yjs updates commute and can be repeated or delivered out of order. Relative positions stay attached to document content. | Authorize edits, persist and replay binary updates, and relay ephemeral presence. |

Use a Yjs document per item. All clients derive the same Yjs seed update from the room's immutable starting text. The feature opens lazily from a task card or retrospective note. A writing member starts collaboration explicitly. Project members can read an existing room. Task editors may write their task descriptions. Retrospective authors and project owners or admins retain their existing edit rights.

The server assigns consecutive revisions while holding the project mutation lock and the document row lock. Each binary update includes a client UUID. A SHA-256 receipt binds the UUID to the payload and survives compaction. PostgreSQL stores the active tail and moves 128 frames at a time into a GZIP snapshot. The snapshot stores the ordered history in a length-prefixed stream. Its uncompressed size is bounded to 2 MiB over at most 16,384 frames. The server keeps Yjs bytes opaque, so its compaction compresses the history and removes individual tail rows. Yjs reconstructs its state from the frames in the browser.

The text visible in the task API, notes list and exports is a projection submitted by a current editor with a confirmed revision. The server accepts a new projection only for the latest persisted revision and applies the existing text checks and entity versioning. The tabs repair stale projections on reconnect. Ordinary task and note edits continue to serve their metadata and direct changes to an active room's text return a conflict. A browser tab can retain unacknowledged changes through a connection drop. Leaving the tab can discard those changes after confirmation.

See [Yjs document updates](https://docs.yjs.dev/api/document-updates), [relative positions](https://docs.yjs.dev/api/relative-positions) and [Ellis and Gibbs on groupware concurrency control](https://doi.org/10.1145/67544.66963).

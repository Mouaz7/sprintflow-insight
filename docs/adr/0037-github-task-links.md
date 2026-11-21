# ADR 0037 — GitHub task links and delivery history

Status accepted.

Extend the existing project webhook from ADR 0032. Store one repository and one AES-GCM-encrypted HMAC secret per project. Project Owners and Admins change repository configuration and status rules. The secret is issued only when the connection is created or replaced. Replacing or disconnecting the webhook invalidates the old URL. Changing status rules leaves the endpoint and secret in place.

The receiver verifies the signature over the raw body before parsing it, then checks the repository and delivery UUID. The project write lock serializes processing with archive, trash and connection changes. The delivery record and its task links commit together with task changes. A unique key rejects repeated delivery IDs within the project. A second uniqueness constraint on the SHA-256 of the raw body rejects an identical payload arriving with a different delivery ID. If a transaction fails, the delivery can be redelivered.

Link organization follows the current project task key. An active task gets one link per repository and PR number or commit SHA. Link URLs are constructed from the configured repository and GitHub identifiers. Incoming URL fields do not supply the link destination. Pull request titles and commit summaries are displayed as text. The delivery history retains event type, received time, repository, delivery ID and aggregate counts. The receiver does not retain the body or webhook headers.

The optional built-in rules default to off. They move a referenced task to Review on `opened` and to Done on a merged `closed` event. A late opening cannot replace a recorded merge. Ordinary task updates preserve status history, realtime events and the required checklist rule. Merged pull requests still publish the existing automation signal. It uses a stable ID so that previously run B15 actions stay deduplicated.

Project readers can open a task’s latest hundred links. Project Owners and Admins can view the latest hundred delivery entries, including in archived projects. Only active projects accept webhook writes. The existing one MiB request limit also covers push payloads.

See [GitHub webhook payloads](https://docs.github.com/en/webhooks/webhook-events-and-payloads) and [signature verification](https://docs.github.com/en/webhooks/using-webhooks/validating-webhook-deliveries).

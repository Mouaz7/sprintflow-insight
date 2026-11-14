# Delivery metric definitions

All metric queries are scoped to the requested project. Selecting a sprint narrows task data to that sprint, and completion-based measures use the sprint recorded on the latest `Done` transition. Sprint dates are interpreted in UTC and include both the start and end dates.

- **Cycle time** is the mean elapsed hours from the latest `In Progress` transition before the latest `Done` transition. Tasks without both valid transitions are omitted.
- **Lead time** is the mean elapsed hours from task creation to its latest `Done` transition. Missing or negative durations are omitted.
- **Throughput** for a project is the number of currently completed tasks whose latest `Done` transition occurred in the previous 14 days. With a sprint selected, it counts currently completed tasks whose latest `Done` transition belongs to that sprint and falls within its scheduled dates when dates are configured.
- **Velocity** is the sum of story-point snapshots captured on the latest persisted `Done` transition for currently completed tasks. A selected sprint includes only completions recorded for that sprint. This keeps later edits from rewriting delivered points.
- **Completed sprint history** replays each task's most recent persisted state at the sprint's actual closure timestamp. Only tasks in `Done` for that sprint at closure contribute points and task counts. Moving, reopening, re-estimating or placing a task in the recycle bin later does not rewrite the closed sprint's delivery. A completion after closure and a task reopened before closure are excluded. Sprint records with missing, future or inconsistent closure timestamps are withheld rather than assigned fabricated values. Permanent deletion removes the affected history under the documented data-removal policy.
- **Burndown** replays persisted task status and point snapshots at the end of each UTC day. Project view covers 14 days for currently visible tasks. Sprint view uses the sprint recorded on each day's latest snapshot, including tasks subsequently moved or trashed, rather than today's membership. A completed sprint stops at its actual closure date and ignores changes after its closure timestamp, including later changes on the same day. The UI withholds a chart when required history is missing or the series contains no remaining estimated work. Permanent deletion removes the corresponding history. Trash timestamps are not workflow events: the curve does not reconstruct historical scope withdrawals caused solely by moving work to the recycle bin.

Current-state measures such as WIP, blocked work, overdue tasks, and completion rate describe the task state at query time. Historical measures require a valid persisted status transition; incomplete legacy records do not receive fabricated timestamps.

Task point and sprint edits also persist state snapshots, even when status does not change.
These snapshots update burndown and historical state without creating a new workflow transition.
They do not restart cycle time, lead time or review waiting time, or count as a new completion.
Moving a story to another sprint (or back to the backlog) moves its linked tasks atomically,
including tasks already in the recycle bin; deletion markers remain unchanged.
Tasks linked to a story must have exactly the story's sprint, including a null sprint
for an unplanned story. Only standalone tasks can choose a sprint independently.

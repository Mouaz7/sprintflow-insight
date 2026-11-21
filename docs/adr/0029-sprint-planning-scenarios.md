# ADR 0029: Sprint-planning proposals from backlog and available capacity

Status: Accepted. Extends [ADR 0011](0011-cp-sat-sprint-optimisation.md).

Sprint planning provides a read-only scenario for a planned or active sprint. It derives candidate stories and effort from a consistent PostgreSQL snapshot after checking the session's current project access. Each scenario can change effective capacity and require or exclude selected stories. Existing backlog, dates, assignments and capacity settings stay as recorded. Archived projects can be read.

Start with the configured B4 capacity in points or whole hours for each working member. Subtract outstanding commitments in the target sprint, including scheduled stories and independent sprint tasks. In point mode the larger of the story's estimate and its outstanding tasks' total is used. In hour mode task estimates are rounded up by assignee to whole hours, with a separate group for unassigned effort. A missing time estimate in a current commitment prevents a proposal until the estimate is provided. Candidate stories with missing effort are shown separately.

The snapshot admits at most 200 backlog and committed stories, 100 working members and 2,000 live tasks. Only unscheduled stories with usable estimates enter the solver. Dependencies between the story's tasks are internal. An unfinished predecessor outside the story must be committed to this sprint or selectable with the story. A predecessor that has been completed satisfies the dependency. A cycle between the resulting story groups is reported, even when the underlying task graph is acyclic.

The core accounts for assigned work against each person and all work against the team. Unassigned effort remains part of the team total and the proposal leaves its assignment open. A team override can reduce the total below the sum of member capacity. To increase it, adjust individual member capacity in the scenario first. A story without a configured business value contributes zero. Selected stories are returned in backlog order with per-resource usage, conflicting locks or exclusion reasons.

The database read transaction ends before native optimization. At most two optimizations run concurrently per application instance. CP-SAT gets its existing two-second budget. The result identifies whether optimality was proved, reports the bound and gap when available, and explains a greedy fallback following a timeout. [Solver benchmarks](../evaluation/sprint-plan-solvers.md) compare the existing algorithms on fixed synthetic inputs.

The drawer loads on demand and has an 8 kB gzip budget. Its measured size is 6.19 kB, bringing the combined JavaScript for all routes to about 338 kB. The aggregate budget is 340 kB and the entry budget remains 125 kB.

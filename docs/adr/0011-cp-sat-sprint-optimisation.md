# ADR 0011: CP-SAT for sprint optimisation

Status: accepted for C1 (algorithmic core). The REST endpoint, UI and evaluation follow in later C1 changes.

## Context

C1 proposes sprint content that maximises business value. The constraints are: total capacity, optional capacity per member, dependencies (a story may be chosen only if each predecessor is done or also chosen), and locked-in/locked-out stories. With one resource and no dependencies this is the 0/1 knapsack problem. With dependencies and several resources it is a multi-dimensional, precedence-constrained knapsack, which is strongly NP-hard. The planner waits for the answer, so the response time must stay bounded. The answer must also say how good the plan is and why stories were left out.

## Decision

Model the problem in **OR-Tools CP-SAT** (`com.google.ortools:ortools-java` **9.15.6755**, released January 2026), in the pure-Java module `com.sprintflowinsight.planning.optimizer`. The module has no persistence or web dependencies.

- One boolean per selectable story. Locked-in stories and their predecessors are fixed to 1. Each candidate predecessor gets an implication `x[s] ⇒ x[p]`. There is one linear capacity row for the total and one per member. The objective is to maximise Σ value.
- Default time limit **2 s**, 8 parallel workers and fixed seed 0. Plans of equal value are tie-broken towards the top of the backlog. The objective is `value·M + (n − position)`, where `M` exceeds the largest possible tie-break sum, so the value always dominates. The preference is dropped automatically if the scaled objective would exceed 2⁵⁰. Without the tie-break, parallel workers returned different but equally valuable plans in 9 of 120 repeated solves; with it, all 120 agreed. The deterministic `interleave_search` was measured as an alternative and was about 50× slower (≈ 0.9 s instead of 19 ms at 100 stories with 5 members), so it is only an opt-in setting. The greedy plan is passed as a solution hint.
- Report `OPTIMAL`/`FEASIBLE`, the objective, CP-SAT's best bound and the relative gap `(bound − objective)/bound`. If no solution is found before the limit, return the greedy plan as `UNKNOWN`, without a bound.
- A shared analysis, used by every solver, removes locked-out stories and stories with unsatisfiable dependencies, recursively. It also detects infeasible locks before any solver runs: a locked-in story that depends on a blocked story, or locked-in work that alone exceeds a capacity. All solvers therefore agree on `INFEASIBLE` and on the explanation.
- Dependency cycles between candidates are rejected as invalid input.

The same `SprintPlanSolver` interface has two baselines for the RQ3 evaluation:

1. **Greedy.** Repeatedly add the story with the highest value per point, together with the unselected predecessors it needs, as long as it fits every capacity. This is fast and has no quality guarantee.
2. **Dynamic programming (DP).** An exact solver for a single resource that runs in O(n·C). Locks and blocked stories are filtered out first. Each remaining story may have at most one free predecessor (a *precedence forest*), and the DP uses the tree-knapsack recurrence over a depth-first preorder. Problems with member capacities, a story with two or more free predecessors, or more than 2·10⁷ table cells are reported as unsupported (`supports() == false`) and are not approximated.

Every plan carries an explanation:
- capacity used and available per resource;
- the binding resources;
- for each excluded story, a reason: `LOCKED_OUT`, `MISSING_DEPENDENCY` (with the blocking predecessors), `CAPACITY`, `LOWER_VALUE_DENSITY`, `NO_ADDED_VALUE` or `NOT_REACHED` (time limit).

An excluded story is judged together with the unselected predecessors it would need. A resource is binding if at least one excluded story would exceed it.

## Alternatives

- **Only an own DP or branch-and-bound.** This is exact only for the single-resource forest case. Supporting member capacities and general dependencies would mean writing and maintaining a MIP solver.
- **MIP solver (SCIP/HiGHS via OR-Tools `MPSolver`, or ojAlgo).** This works for a pure 0/1 linear model. CP-SAT is the stronger default for pure boolean problems in OR-Tools, gives the bound and gap the same way, and makes later extensions easy (story splitting, assignment, `AddMaxEquality`-style fairness).
- **Timefold/OptaPlanner.** This is local search without a proven bound, so it cannot report an optimality gap. It is also a larger framework.

## Consequences

- **Native code.** The dependency brings JNI libraries for linux-x86-64/aarch64, macOS x86-64/aarch64 and Windows x86-64, 19–26 MB each. They make the executable jar about 112 MB larger (about 205 MB in total). The Docker build could keep only linux-x86-64 if image size matters. `Loader.loadNativeLibraries()` extracts the library to the temporary directory, which must be writable. Loading and solving from the packaged Spring Boot jar (nested `jar:nested:` URLs) was verified with Boot's `PropertiesLauncher`. The Docker runtime image (`eclipse-temurin:25-jre-jammy`, Ubuntu 22.04, glibc 2.35) satisfies OR-Tools' glibc requirement, so no base-image change is needed. An Alpine/musl image would not work.
- **Native access.** Java 25 warns when JNI code is loaded without native access. The jar manifest has `Enable-Native-Access: ALL-UNNAMED` (honoured by `java -jar`), and Surefire runs with `--enable-native-access=ALL-UNNAMED`.
- **Dependencies.** OR-Tools brings `protobuf-java` 4.33.1 and JNA 5.14. They are not managed by Boot, so the OSV scan covers them. On Java 25, protobuf prints a `sun.misc.Unsafe` deprecation warning at startup; it is harmless until a later JDK removes those methods.
- **Testing.** Tests use JUnit examples and jqwik properties on random instances. Every plan respects capacities, dependencies and locks. CP-SAT is ≥ greedy, equals the DP on single-capacity forests, and equals brute force on ≤ 10 stories. Results are deterministic. The package is included in PIT. `SprintPlanBenchmarkTest` (JUnit tag `benchmark`, excluded by default) writes `target/benchmark/sprint-plan.csv` with a fixed seed for RQ3.
- **Determinism.** The result is reproducible as long as CP-SAT proves optimality within the limit. When the wall-clock limit cuts the search short, the plan can differ between machines; the status and gap say so.

## Sources

- [CP-SAT solver](https://developers.google.com/optimization/cp/cp_solver)
- [OR-Tools Java on Maven Central](https://central.sonatype.com/artifact/com.google.ortools/ortools-java)
- [`sat_parameters.proto`](https://github.com/google/or-tools/blob/stable/ortools/sat/sat_parameters.proto) (`interleave_search`, `max_time_in_seconds`)

# SprintFlow Insight — evaluation

## Introduction

This evaluation asks how SprintFlow's workspace responds under a fixed read load, how its planning models behave on known workloads, and whether live features remain usable as demand grows. RQ1 compares the application before and after the runtime and feature upgrades. RQ2–RQ4 evaluate forecasting, sprint planning and late-task risk. RQ5 covers accessibility and perceived usability. RQ6 measures live task and collaborative-text delivery.

## Background

SprintFlow combines a project-scoped PostgreSQL application, a React client, scenario models and a STOMP transport. Forecasts estimate future throughput with count distributions and bootstrap samples. Sprint planning compares feasible plans under capacity and dependency constraints. The risk model predicts whether an unfinished task stays incomplete at sprint end. Project events have a sequenced replay log, while collaborative descriptions use Yjs updates.

## Method

| Question | Population and protocol | Measures |
| --- | --- | --- |
| RQ1 | One fixed 23-task Scrum fixture. One warm-up user for ten iterations, then five users for 20 seconds. Matched Java heap and processor flags, same workstation and PostgreSQL 16.15. | Request p95, throughput, failures and database statement calls over the complete fixture run. |
| RQ2 | Three Apache Jira project series and three seeded 26-week synthetic series. Eight starting weeks, two-week horizon and 17 rolling origins per series. Forecast seed `20261120`, generator seed `2026112201`. | CRPS, MAE, central interval coverage and PIT histograms. |
| RQ3 | Sixty-four dependency and capacity fixtures at 20, 50, 100 and 200 stories. Seed `20261115 + 1000 × size + instance`. | Feasibility, objective, bound, optimality gap and solution time. |
| RQ4 | Apache Jira feature snapshots with a chronological 2021-01-01 boundary. Feature data predates the outcome, and straddling issues are dropped. | ROC AUC, Brier score and calibration bins. |
| RQ5 | Browser checks of representative flows and a separate usability session for each participant. Use Brooke's ten-item instrument after the tasks and before debriefing. | Accessibility findings by criterion and aggregate SUS for at least five complete participant rows. |
| RQ6 | Ten, 50, 100, 250 and 500 authenticated STOMP clients on loopback. Three measured task events and Yjs updates per level after a warm-up event. | Complete delivery count and median, p95 and maximum delivery latency. |

The WCAG 2.2 AA evaluation combines automated checks with keyboard, focus, zoom and assistive-technology review of the selected flows. The usability protocol asks participants to create a task, move it, find a report and resolve a conflicting edit using fixtures. Capture completion and observations during the task. Collect ten anonymous ratings immediately afterward. Store only ordered responses `q1,q2,q3,q4,q5,q6,q7,q8,q9,q10` with values 1–5 in an external CSV. [WCAG 2.2](https://www.w3.org/TR/WCAG22/) · [Brooke's SUS chapter](https://www.taylorfrancis.com/chapters/edit/10.1201/9781498710411-35/sus-quick-dirty-usability-scale-john-brooke).

## Implementation

Run `make eval` with Java 25, Maven and Node 24. Results go to `artifacts/evaluation` by default. The runner reproduces the committed aggregate Jira analyses, generates the synthetic series and invokes the existing solver and risk benchmarks. It keeps the private usability CSV outside the repository.

- Set `EVAL_SUS_CSV` to the anonymous response CSV for SUS scoring.
- For RQ1, provide `EVAL_HTTP_BEFORE_URL`, `EVAL_HTTP_AFTER_URL`, `EVAL_HTTP_BEFORE_DATABASE` and `EVAL_HTTP_AFTER_DATABASE`. Use disposable apps on `127.0.0.1`, k6 2.4.0 and a PostgreSQL server with `pg_stat_statements` loaded and installed in both databases. Use `PGHOST`, `PGPORT` and `PGUSER` for the fixture database connection.
- For RQ6, install the locked script dependencies with `npm ci --prefix scripts` and set `EVAL_REALTIME_SINGLE_URL` and `EVAL_REALTIME_CLUSTER_URL` to the respective disposable endpoints. Use the same application image and broker across the replica comparison. `EVAL_REALTIME_CLIENTS` can select a smaller smoke sample from the supported range.

## Results

### RQ1 — workspace reads

The A3 package from `f1cc7eb` on Java 17 and the newer B13 app on Java 25 served the same fixture on 22 November 2026. k6 recorded zero HTTP errors in both measured windows.

| Variant | Measured requests | Requests/s | p95 (ms) | SQL calls over full workload |
| --- | ---: | ---: | ---: | ---: |
| A3 | 2,295 | 114.75 | 18.08 | 24,629 |
| B13 | 2,265 | 113.25 | 18.22 | 26,239 |

The measured request rate differed by 1.5 requests/s. The full-workload SQL bracket includes fixture creation, warm-up, scheduler work and cleanup. [Before](results/http-before.json) · [After](results/http-after.json) · [SQL counts](results/http-sql-counts.csv).

### RQ2 — forecast calibration

The checked-in Jira snapshot reproduced byte for byte. Gamma–Poisson selected for HADOOP, exponentially weighted Poisson for HBASE and bootstrap for KAFKA. HBASE's observed 95% coverage reached 47.1% during a period with bulk closures. [Jira backtest](apache-jira-backtest.md).

| Synthetic series | Selected model | CRPS | MAE | 50% / 85% / 95% coverage |
| --- | --- | ---: | ---: | --- |
| Stable Poisson | Gamma–Poisson | 2.768 | 4.294 | 47.1% / 88.2% / 100% |
| Step change | Exponentially weighted Poisson | 8.704 | 10.235 | 41.2% / 47.1% / 52.9% |
| Intermittent | Bootstrap | 11.508 | 17.059 | 23.5% / 94.1% / 100% |

[Synthetic backtest output](results/forecast-synthetic.tsv).

### RQ3 — solver quality

CP-SAT proved an optimum in all 64 fixed cases in the recorded comparison. It matched dynamic programming across the 32 supported dependency forests. The largest five-member group improved on greedy in eight of eight cases, with a recorded median CP-SAT time of 116.8 ms. The timing and bound summaries are retained with the model and fixture version. [Solver benchmark and raw CSV](sprint-plan-solvers.md).

### RQ4 — late-task risk

The 48-observation holdout contained 12 late outcomes. The smoothed pooled base rate returned AUC 0.5000 and Brier 0.1948. Pooled logistic regression returned AUC 0.4560 and Brier 0.3503. Calibration bins show substantial underestimation in part of the holdout. [Cohort, metrics and calibration](apache-jira-risk.md).

### RQ5 — usability and accessibility

The browser suite includes axe checks and keyboard flows for the documented views, including offline recovery. Participant response data are pending. The report has no measured SUS aggregate yet. Complete a criterion-by-criterion manual review before making an AA conformance claim. [Browser verification](../testing.md).

### RQ6 — realtime and CRDT delivery

The local single-instance run delivered all three samples per client in both streams at each load level.

| Concurrent clients | Realtime p95 (ms) | CRDT p95 (ms) |
| ---: | ---: | ---: |
| 10 | 34.70 | 11.67 |
| 50 | 39.30 | 19.37 |
| 100 | 52.14 | 24.51 |
| 250 | 68.99 | 44.10 |
| 500 | 96.97 | 90.36 |

The table uses the in-process broker and one workstation. The controlled replica comparison uses the Kubernetes fixture with RabbitMQ enabled in both variants. [Single-instance output](results/realtime-single.json).

## Discussion

Read performance was close across the two measured packages. SQL work rose in the broader runtime and feature bundle. More repeated runs with controlled host load and per-request instrumentation would help attribute that difference. Forecast calibration depended on the throughput process, particularly after changes in work rate. CP-SAT improved more often as the constrained planning fixture grew. The late-task holdout favored the simple base-rate benchmark and supports further calibration work before operational use.

## Threats to validity

The k6 comparison measures two releases and all their changes on one host. The forecast windows overlap and use tracker resolution dates affected by bulk closures. The risk holdout is small and drawn from DATALAB. Solver wall-clock results depend on the host and the fixed timeout. Realtime probes share one account, one project and one workstation, and measure delivery from the local client rather than geographical latency. Accessibility and perceived usability require their separate review protocols.

## Conclusion

The measured read workload retained a p95 near 18 ms. Model quality varied across workloads, with the clearest improvement observed in constrained sprint planning. Fixed inputs and the evaluation runner make those comparisons repeatable. Human usability and the controlled two-replica sample are tracked as outstanding evidence.

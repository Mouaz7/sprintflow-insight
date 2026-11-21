# Late-task risk on Apache Jira sprint history

The [Apache Jira REST API](https://issues.apache.org/jira/rest/api/2/search) returned 2,553 public issues in DATALAB, HDDS, MESOS, OFBIZ and SDAP. The query covers issues created from 2019 through 2024 with a populated Sprint field. MESOS and OFBIZ were added for historical coverage by selecting the two largest additional projects with closed-sprint issues created in 2019–2020. The [capture manifest](apache-jira-risk-source.json) fixes the query, row counts and SHA-256 checksum.

The capture replays Sprint, status, estimate, assignment and flag changes and uses comment timestamps. It takes each issue's first eligible 02:40 UTC observation while it belongs to a sprint and is unfinished. The outcome is its status at the earlier of the Jira sprint's scheduled end and recorded completion. The output contains 1,137 numeric observations with project codes and UTC dates. Issue keys, identities, comment contents and sprint text stay out of the output.

The public cohort covers issues with a recorded closed sprint and complete histories. It approximates column WIP and age references from this selected cohort. Jira blocks use transitions into Blocked or Jira's impediment flag. Historical dependency depth is set to zero because prerequisite state at observation time cannot be reliably reconstructed from the captured fields. Sprint lengths and task policies vary across projects. The live product reads all eight features directly from its own workspace.

## Chronological holdout

The boundary is 2021-01-01 UTC. Training uses only observations with sprint outcomes strictly before the boundary. Test observations start on or after the boundary. The 110 issues whose feature and outcome dates straddle it are dropped. One observation per Jira issue prevents repeat issues across the split. Of 1,137 observations, 979 train and 48 test. The test set has 12 late outcomes, all from DATALAB.

| Training scope and model | Training examples (late) | ROC AUC | Brier |
| --- | ---: | ---: | ---: |
| DATALAB, production cold-start heuristic | 546 (35) | 0.4491 | 0.2777 |
| DATALAB, smoothed base-rate baseline | 546 (35) | 0.5000 | 0.2216 |
| Pooled projects, L2 logistic regression | 979 (329) | 0.4560 | 0.3503 |
| Pooled projects, calibrated heuristic | 979 (329) | 0.4491 | 0.2957 |
| Pooled projects, smoothed base-rate baseline | 979 (329) | 0.5000 | 0.1948 |

The DATALAB model uses the heuristic because the earlier data has 35 late outcomes, below the production threshold of 80 examples in each class. Pooling is an offline comparison. The deployed model trains on its own project.

Neither fitted approach beats the smoothed base-rate baseline on this holdout. The [calibration curve data](apache-jira-risk-calibration.csv) shows that the pooled regression assigned 31 tasks less than 10% risk. The mean estimate was 3.9% and 38.7% of those tasks were late. The DATALAB heuristic put 35 tasks below 10%, with a mean estimate of 0.2% and an observed late rate of 34.3%. The small, single-project holdout and changed completion rate limit what this result says about other projects. The evaluation provides no evidence for trusting these probabilities as well-calibrated on new teams.

## Reproduction

The fixed fixture uses deterministic feature extraction, no random splitting and deterministic Newton fitting. Recompute [the metrics](apache-jira-risk-metrics.csv) and calibration bins from the checked-in CSV with Java 25:

```sh
cd backend
./mvnw test -Dtest=JiraRiskEvaluationTest -Dtest.excludedGroups=none -Djacoco.skip=true
```

The test writes both CSVs in `backend/target/benchmark/`. Fetch a new public snapshot from the repository root with Node 24 and an empty output directory:

```sh
node scripts/evaluation/capture-jira-risk.ts path/to/empty-output-directory
```

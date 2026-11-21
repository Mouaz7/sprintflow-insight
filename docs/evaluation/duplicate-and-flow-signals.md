# Evaluation of duplicate suggestions and flow signals

## Duplicate suggestions

The [Apache Jira REST API](https://issues.apache.org/jira/rest/api/2/search) supplied HADOOP, HBASE and KAFKA issues created from 2019 through 2024 with `issueLinkType = duplicates`. Each public, same-project Duplicate relation contributes one positive title pair. The controls pair each positive's source issue with three unlinked source issues sampled from its 20 closest creation dates. The capture uses seed `20261121`. It retains only component project, pair label and the PostgreSQL score. [Query and checksums](jira-duplicate-source.json).

The threshold grid starts at 0.05 in steps of 0.025. Eligible points must meet the indexed retrieval floor of 0.325 and at least 90% precision on HADOOP and HBASE. The rule chooses the highest recall among eligible points. The selected 0.35 threshold is then evaluated once on the held-out KAFKA pairs. [Precision–recall curve from calibration](jira-duplicate-curve.csv).

| Cohort | Positive pairs | Proxy controls | True positives | False positives | Precision | Recall |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| HADOOP + HBASE calibration | 224 | 672 | 62 | 5 | 92.5% | 27.7% |
| KAFKA holdout | 312 | 936 | 95 | 6 | 94.1% | 30.4% |

The evaluation favors fewer irrelevant suggestions. It misses most Jira duplicates, including many whose titles use different words. The negative labels mean there was no direct Duplicate link between the paired issues in the capture. True production prevalence, result ranking and the five-item display limit can change the precision seen in the workspace.

Replay the threshold from the checked-in scores without network or database access:

```sh
node scripts/evaluation/capture-jira-duplicates.ts --replay docs/evaluation/jira-duplicate-scores.csv
```

Use PostgreSQL 16 with the app's V29 migration for a fresh public capture. Set `C4_EVALUATION_PG_URL` to that evaluation database and pass an empty output directory:

```sh
node scripts/evaluation/capture-jira-duplicates.ts path/to/empty-output-directory
```

## EWMA control limits

Using seed `20261121`, 1,000 simulated Poisson histories per row generate 28 baseline days and seven monitoring days. The test reads the final day's signal. The baseline and monitor rates below are expected counts per day. [Raw results](ewma-flow-signals.csv).

| Baseline rate | False alerts with stable flow | Detected 50% decrease | Detected 50% increase |
| --- | ---: | ---: | ---: |
| 2 | 1.1% | 1.2% | 14.3% |
| 8 | 0.5% | 58.1% | 53.9% |
| 20 | 0.5% | 99.2% | 92.7% |

Small daily volumes make the chart insensitive to gradual changes. This simulation assumes independent counts. Weekday patterns, correlated observations and batch closures change the rate of signals in a live project.

```sh
cd backend
./mvnw test -Dtest=EwmaControlChartEvaluationTest -Dtest.excludedGroups=none -Djacoco.skip=true
```

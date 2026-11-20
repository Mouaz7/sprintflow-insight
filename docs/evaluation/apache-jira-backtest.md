# Apache Jira throughput backtest

We captured resolution dates from the [public Apache Jira search API](https://issues.apache.org/jira/rest/api/2/search) on 20 November 2026 for Hadoop Common, HBase and Kafka. The fixed window spans 26 full ISO weeks from 3 January through 3 July 2022 in UTC. Each issue currently in the Done status category contributes once on its recorded resolution date. The [source manifest](apache-jira-source.json) records filters, counts and control hashes. The [weekly counts](apache-jira-weekly.csv) contain no individual issue records or free text.

This snapshot comes directly from Apache Jira. The [Montgomery dataset](https://doi.org/10.5281/zenodo.15719919) has a separate scope and capture date. Its Apache export can be imported for independent evaluation using the B19 subset converter.

Use eight starting training weeks, a two-week forecast horizon and at least six origins. Each project provides 17 historical origins. Use seed **20261120** with 10,000 bootstrap trials. The selected model has the lowest mean CRPS. Coverage measures how frequently the actual two-week total falls inside each central prediction interval. The [full results](apache-jira-backtest.tsv) include every candidate and all ten PIT bins.

| Project | Issues | Selected model | Mean CRPS | MAE | Observed 50% / 85% / 95% coverage |
| --- | ---: | --- | ---: | ---: | --- |
| HADOOP | 183 | Gamma–Poisson | 2.897 | 3.941 | 47.1% / 70.6% / 88.2% |
| HBASE | 1,979 | Exponentially weighted Poisson | 166.120 | 169.471 | 17.6% / 29.4% / 47.1% |
| KAFKA | 299 | Bootstrap Monte Carlo | 3.980 | 6.059 | 58.8% / 94.1% / 100% |

The HBase series includes 549 and 762 closures in two June weeks. Its selected model has the lowest CRPS among these candidates while the 95% interval covers fewer than half the observed totals. These results describe model selection on overlapping validation windows at a single horizon. They provide no independent holdout estimate, and resolution dates can include bulk closures or tracker cleanup. Project-level accuracy and the ranking can change with the period, horizon or tracker conventions.

## Reproduce with Java 25

From the repository root:

```sh
(cd backend && ./mvnw -DskipTests compile)
mkdir -p backend/target/forecast-evaluation
javac -cp backend/target/classes -d backend/target/forecast-evaluation scripts/evaluation/ForecastEvaluation.java
java -cp backend/target/classes:backend/target/forecast-evaluation \
  com.sprintflowinsight.reporting.ForecastEvaluation docs/evaluation/apache-jira-weekly.csv
```

Use the committed weekly CSV to reproduce the reported numbers. The capture script can request a fresh snapshot from Apache Jira into a new directory:

```sh
node scripts/evaluation/capture-jira-throughput.ts artifacts/jira-throughput
```

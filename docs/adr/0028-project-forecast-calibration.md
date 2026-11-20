# ADR 0028: Compare project forecasting models at a fixed horizon

Status: Accepted. Extends [ADR 0010](0010-probabilistic-forecast-models.md).

## Decision

The **Model comparison** panel on **Forecasts** compares the three existing models on the selected project's full weeks or completed sprints. Its read endpoint derives the same history as the delivery forecast for that basis. Project roles, archived reads and recycle-bin exclusion follow the existing forecast access policy. The response has a bounded cache key for the basis and input history. The panel requests it when expanded.

At least 15 complete periods and at least one positive period are needed. At each rolling origin after eight training periods, every model predicts the sum over the following two periods. The lowest mean CRPS wins. The model's prediction over the next two periods uses all available history and shows central 50%, 85% and 95% intervals. A separate readout includes observed coverage, mean absolute error of the median, and the chosen model's PIT histogram.

The two-period horizon keeps the validation and prediction directly comparable. The existing deadline planner retains its joint bootstrap paths and its one-sided delivery confidence. Changing that planner to a selected model would require a joint process across deadlines and a wider set of evaluated horizons. An epic uses project capacity for both readouts.

Backtests use the same deterministic seed as delivery forecasts, derived from the immutable history basis and counts. The projected distribution starts with a fresh generator at that seed to isolate resampling from evaluation order. A bounded cache avoids storing selections that would outlive edits to project history.

## Verification

The scores and observed coverage vary by project. A minimum number of observations enables the comparison and does not guarantee calibration. The reproducible [Apache Jira evaluation](../evaluation/apache-jira-backtest.md) reports the input, seed, candidate scores, coverage, PIT and limitations for three project histories.

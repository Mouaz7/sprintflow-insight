# ADR 0010: Probabilistic forecast models selected by backtested CRPS

Status: accepted for C2 (algorithm core). Builds on the B6 Monte Carlo forecast.

## Context

B6 gives one bootstrap Monte Carlo forecast. Bootstrap resampling has known blind spots. It cannot forecast beyond the largest observed period, it ignores recency, and with a few weeks of history it produces overconfident, lumpy distributions. Projects differ in stability and history length, so no single model is best everywhere. Users also need to know how far to trust the 85 % date. We need several models, a measure of their out-of-sample quality on each project's own history, and a rule for choosing between them.

## Decision

Add a dependency-free Modulith module `forecast` with three models behind one interface `ForecastModel`, each returning an integer-valued predictive distribution:

| Model | Why it is included |
| --- | --- |
| Bootstrap Monte Carlo (10 000 trials, injected `RandomGenerator`) | Empirical baseline with exchangeability assumptions; B6 web paths are detailed in ADR 0021 |
| Gamma-Poisson with conjugate update, exact negative-binomial predictive | Propagates rate uncertainty, so intervals widen when history is short; closed form, so results are exact and need no sampling |
| Exponentially weighted Poisson (half-life 4 periods) | Adapts quickly to level shifts such as team growth |

Evaluate each model with **rolling-origin backtesting** and choose the one with the lowest **mean CRPS**. CRPS is a strictly proper scoring rule for the whole predictive distribution, so a model cannot improve its score by shading its intervals, and it rewards calibration and sharpness in one number. It reduces to the absolute error for point forecasts, is expressed in items, and for integer-valued forecasts is computed exactly as Σₖ (F(k) − 𝟙{k ≥ y})². Interval coverage (50/85/95 %), the non-randomised PIT histogram and MAE of the median are also reported. They explain the choice to users but do not drive it, because coverage alone can be gamed with very wide intervals and MAE ignores uncertainty.

Selection requires a minimum amount of history: 8 training periods, a 2-period horizon and 6 origins, so 15 periods. Below that, the module returns an explicit `InsufficientHistory` result rather than a choice based on noise.

## Alternatives considered

- **Log score** (log predictive density). It is also proper, but it is infinite whenever an outcome has zero probability. That happens routinely for the bootstrap, whose support is limited to sums of observed periods.
- **Choosing by coverage or MAE.** Coverage rewards uninformative wide intervals, and MAE ignores the distribution.
- **Richer models** (negative-binomial regression with seasonality, state-space or Bayesian hierarchical models across projects). They are heavier to explain and test, and typical team histories of 15 to 50 weeks are too short to support them. They can be added later behind the same interface and judged by the same backtest.
- **Sampling the Gamma-Poisson predictive.** Rejected because the exact pmf is cheap, deterministic and easier to test.

## Consequences

- Forecasts are reproducible with a fixed seed. The exact models need no seed at all.
- The module has no Spring, persistence or HTTP dependencies (`allowedDependencies = {}`). Wiring (REST, persistence of selections, UI explanation, `docs/evaluation/` results) is a separate step of C2.
- Backtesting costs roughly origins × models forecasts. With 10 000 bootstrap trials this takes milliseconds to tens of milliseconds per project. Results can be cached per project and recomputed when history changes.
- The method, formulas and limitations are documented in `docs/metrics-definition.md`.

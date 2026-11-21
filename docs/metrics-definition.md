# Delivery metric definitions

All metric queries are scoped to the requested project. Selecting a sprint narrows task data to that sprint, and completion-based measures use the sprint recorded on the latest `Done` transition. Sprint dates are interpreted in UTC and include both the start and end dates.

- **Cycle time** is the mean elapsed hours in the latest uninterrupted `In Progress` / `Review` run ending in the latest `Done` transition (B5). A return to `To Do` or a completed-and-reopened task starts a new run. Moving between active columns keeps that run, and a direct move from `Review` to `Done` is measurable. Tasks without both valid transitions are omitted.
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

## Delivery forecasts (B6)

The project forecast API uses 10,000 joint bootstrap paths when history contains completed work. Without usable history or observed completions, it reports zero simulations and unknown positive-work probabilities; zero work is trivially complete. It uses an injected RandomGenerator and a reproducible history seed. It samples complete historical period counts with replacement and assumes uniform accrual within each future period. Calendar days include weekends. All questions share the same paths, so changing a deadline leaves completion dates unchanged and changing remaining work leaves by-date amounts unchanged.

Throughput counts each currently visible Done task once at its latest real Done transition, collapsing same-status snapshots. It includes zero-completion weeks but excludes the first partially observed ISO week and current incomplete week, retaining at most 104 full UTC weeks. Velocity replays retained status/point/sprint snapshots at completed-sprint closure, retaining at most 52 sprints. Future period length is the lower median valid scheduled sprint length, or 14 days if unavailable. These histories use current retrospective scope: later reopen/trash/deletion can change them.

- Completion dates at 50/85/95% are empirical quantiles of path completion days. Paths unfinished after 1,825 days stay in the denominator; a confidence level requiring one has no reported date.
- By-date quantities are the largest x reached in at least the selected share of paths. Thus higher confidence produces a lower, more cautious amount. A target probability is the fraction reaching its quantity by its date.
- Histograms return at most 20 inclusive integer ranges. Completion histograms omit censored paths only visually; their count is shown separately and is retained in all probability calculations.
- Dates use UTC calendar-day differences. Today allows zero additional days. The active sprint's remaining recorded quantity/deadline supplies the default target, otherwise remaining work is tested four periods ahead. Past sprint deadlines permit no additional delivery; deadlines beyond the horizon have no target probability. Custom dates outside the horizon are rejected.

An epic filters remaining tasks through its stories while using whole-project capacity history. Forecast dates therefore assume that capacity is assigned to the chosen work. Missing task history, taskless stories and zero-point open work are disclosed; an explicit planning amount can include work not yet recorded. Automatic amounts exceeding 10,000 units are rejected instead of truncated. The sprint target is a quantity proxy, not a judgement of qualitative goal acceptance.

The six-period minimum warning is guidance, not validation. Period independence, stable capacity and within-period uniform delivery are assumptions. Seasonality, autocorrelation, varying sprint cadence, dependency constraints and unseen capacity changes can make the ranges misleading. A constant history can yield 100% simulation probability without guaranteeing delivery. No model comparison or out-of-sample calibration is claimed here. See [ADR 0021](adr/0021-joint-bootstrap-delivery-forecasts.md) for decisions and methodological context; C2/E1 retain responsibility for comparison and evaluation.

## Probabilistic forecasts and backtesting (C2)

The `forecast` module (`com.sprintflowinsight.forecast`) turns a throughput history into a **predictive distribution** of the number of items completed in the next *t* periods. Planning answers are derived from it. The algorithms are pure Java. The project forecast page exposes their comparison and the selected model’s prediction for the evaluated horizon. [ADR 0028](adr/0028-project-forecast-calibration.md) describes the wiring.

### Input

- A **history** of non-negative counts per consecutive period, oldest first. The normal case is items completed per week (`ThroughputHistory.weekly`). Sprint velocity (`sprintVelocity`) uses one period per sprint and story points as the unit.
- Calendar days become fractional periods (`days / periodDays`). The models assume completions accrue evenly within a period.
- Randomness (the bootstrap only) comes from an injected `java.util.random.RandomGenerator`. With a fixed seed, forecasts and backtests are reproducible.

### Models

All three implement `ForecastModel.forecast(history, t)` and return a `DiscreteDistribution`: a pmf on a contiguous integer range with its cdf, quantiles, mean and variance.

1. **Bootstrap Monte Carlo** (`bootstrap-monte-carlo`, C2 reference core). Each of 10 000 trials (the default) sums ⌊t⌋ periods drawn with replacement from the history. For a fractional remainder *f*, each item of one more drawn period is kept with probability *f* (binomial thinning). The result is the empirical distribution of the trial sums. It needs no distributional assumption, but it can never forecast a period larger than the largest one observed. It also gives no weight to recency.
2. **Bayesian Gamma-Poisson** (`gamma-poisson`). Weekly counts are modelled as *x*ᵢ ~ Poisson(λ) with prior λ ~ Gamma(α₀, β₀) (shape, rate). The conjugate update after *n* periods with total *s* gives the posterior Gamma(α₀ + *s*, β₀ + *n*). The posterior predictive total over *t* periods is **negative binomial** with *r* = α₀ + *s* and *p* = (β₀ + *n*)/(β₀ + *n* + *t*):

   P(K = *k*) = Γ(*k* + *r*) / (*k*! Γ(*r*)) · *p*^*r* (1 − *p*)^*k*, with mean *r*(1 − *p*)/*p* = *t*(α₀ + *s*)/(β₀ + *n*).

   It is computed exactly, not sampled. The pmf starts at the mode, uses the ratio P(*k*+1)/P(*k*) = (*k* + *r*)/(*k* + 1)·(1 − *p*) in both directions, and is normalised. Tails are truncated once a term falls below 10⁻¹⁷ of the modal probability, so the lost mass is about 10⁻¹⁵. Starting at the mode keeps every term ≤ 1, so there is no underflow even for means in the thousands. The default prior is α₀ = β₀ = 0.5: a mean of one item per period, weighted as half an observed period. Both are configurable.
3. **Exponentially weighted Poisson** (`ewma-poisson`). The rate is the weighted mean λ̂ = Σ *w*ᵢ*x*ᵢ / Σ *w*ᵢ with *w*ᵢ = *d*^(*n*−1−*i*) and *d* = 0.5^(1/*h*). The default half-life is *h* = 4 periods. The forecast is Poisson(λ̂·*t*), computed exactly the same way. It reacts quickly to level shifts, but it treats λ̂ as known, so its intervals are too narrow when data are scarce or over-dispersed.

The C2 comparison and its selected model’s prediction use two whole periods. The B6 deadline planner uses uniform accrual along joint paths and can answer questions at fractional horizons. The C2 scores assess only the two-period model forecasts.

### Derived answers (`ForecastAnswers`, C2 core)

- **(a) Completion date for *N* items** at confidence *c* ∈ {50, 85, 95 %}: the first day *d* with P(*S*(*d*) ≥ *N*) ≥ *c*, where *S*(*d*) is the predictive total for *d* days. The search doubles *d*, then bisects. The answer is empty when even five years are not enough. For the bootstrap model, Monte Carlo noise can move the answer by about one day near the threshold.
- **(b) Items by a date** at confidence *c*: the largest *x* with P(*S* ≥ *x*) ≥ *c*. Higher confidence never gives more items.
- **(c) Probability to reach a target** of *m* items in the remaining *d* days: P(*S*(*d*) ≥ *m*) = 1 − *F*(*m* − 1).

### Backtesting (`Backtester`) and model selection

**Rolling origin.** At each origin *o* = *m*, *m* + 1, …, *n* − *h*, a model sees only periods [0, *o*). It forecasts the total of the next *h* periods, and the forecast is scored against what actually happened. The defaults are *m* = 8 training periods and *h* = 2. Each origin produces the following:

- **CRPS**, the continuous ranked probability score: CRPS(*F*, *y*) = ∫ (*F*(*x*) − 𝟙{*x* ≥ *y*})² d*x*. For an integer-valued forecast *F* is a step function with unit steps, so the integral is exactly Σₖ (*F*(*k*) − 𝟙{*k* ≥ *y*})² over the integers between the support and *y*. It equals E|*X* − *y*| − ½E|*X* − *X*′|, is ≥ 0 and is in item units. It is 0 only for a point forecast that hits *y*, and it reduces to the absolute error for any point forecast. Lower is better.
- **Interval coverage**: the share of observations inside the central interval [*q*((1−*c*)/2), *q*((1+*c*)/2)] for *c* = 50/85/95 %. *q* is the left-continuous quantile (the smallest *x* with *F*(*x*) ≥ *p*). On the integers these intervals contain at least *c* of the mass, so a well-calibrated model covers slightly **more** often than nominal.
- **PIT histogram**: the non-randomised PIT for count data (Czado, Gneiting & Held, 2009). For each case, *F*(*u* | *y*) is 0 below *F*(*y*−1), 1 above *F*(*y*), and linear between them. The 10 bins are the averaged increments of *F*(*u* | *y*) and sum to one. A calibrated model gives a flat histogram. A U shape means the intervals are too narrow, and a hump means they are too wide. Mass at one end indicates bias.
- **MAE**: the mean absolute error of the forecast median.

**Selection.** The candidate with the lowest mean CRPS wins, and ties keep the earlier candidate (Gamma-Poisson, EWMA, bootstrap). CRPS is a proper score, so it rewards calibration and sharpness together. Coverage, PIT and MAE are kept in every report to explain the choice. **Minimum-data rule:** selection requires at least 6 origins, that is *m* + *h* + 5 = 15 periods with the defaults. Below that, the result is `InsufficientHistory(available, required)`, and callers should show no model choice (or fall back to the Gamma-Poisson model, which degrades gracefully to its prior).

### Project model selection and evidence

Open **Model comparison** on the forecast page. Each project and history basis uses the lowest mean CRPS to select a model at the two-period horizon. At least 15 complete periods and one positive count are required. The prediction shows the total median and central 50%, 85% and 95% intervals from the selected model. The table reports observed coverage, CRPS and MAE for all candidates. The PIT figure shows whether outcomes are concentrated in particular parts of the selected predictive distribution.

The [Apache Jira evaluation](evaluation/apache-jira-backtest.md) uses a fixed seed and a committed aggregate of weekly resolution counts. HADOOP, HBASE and KAFKA select different models. Interpret the HBASE coverage alongside the bulk closures and the small number of overlapping origins.

### Limitations

- All three models assume periods are exchangeable apart from EWMA's recency weighting. Seasonality, holidays, team changes and scope changes are not modelled.
- Rolling-origin windows overlap when *h* > 1, so the per-origin scores are correlated. With short histories, differences in mean CRPS between models can be noise.
- The Poisson-type models treat items as independent arrivals. Batch completions (many items closed on one day) are over-dispersed, so the EWMA intervals will be too narrow. Gamma-Poisson only partly absorbs this through parameter uncertainty, which vanishes as history grows.
- Velocity in story points is not a count of independent events. The Poisson models only approximate it, so prefer item throughput when both are available.
- The forecast covers future throughput, not scope. The *N* items remaining are taken as given.

## Daily flow signals (C4)

Inflow is the number of live tasks created per complete UTC day. Throughput counts transitions into Done in those same days, with at most one completion per task per day. Consecutive Done snapshots from later metadata edits are skipped. Current project visibility determines which task history contributes. Tasks whose historical dates were preserved during import contribute at those preserved dates.

For both series use the 28 complete days before the seven monitored days. Let μ and s² be their sample mean and variance. Start the statistic at z₀ = μ with variance v₀ = 0. For each monitored count xₜ, update zₜ = 0.25 × xₜ + 0.75 × zₜ₋₁ and vₜ = 0.25² × max(s², μ, 0.25) + 0.75² × vₜ₋₁. The limits are max(0, μ − 3√vₜ) and μ + 3√vₜ. A strict crossing signals an unusual run given the baseline. Weekends, calendar changes, volume shifts and imports can change the signal. [Method, operational behavior and evaluation](adr/0031-duplicate-and-flow-signals.md).

## Late-task risk (C3)

The risk model estimates the probability that an unfinished task in the active sprint will be **unfinished at sprint end**. The pure Java model lives in the `risk` module. PostgreSQL supplies project-scoped features and stores the nightly model. The Tasks view shows the probability, the model source and up to three explanations. See [ADR 0012](adr/0012-late-task-risk-model.md) and [ADR 0030](adr/0030-nightly-project-task-risk.md).

### Features

All features describe the work item, its column or the sprint. Every value is finite and non-negative (`RiskFeatures`).

| Feature | Definition |
| --- | --- |
| Age vs column p85 | Hours in the current column divided by the 85th percentile (linear interpolation, Hyndman–Fan type 7) of project column spans that ended in the preceding 180 days. The reference is at least one hour; without history the ratio is 1. |
| Times blocked | Number of times the task has been marked blocked since V28, including tasks that were blocked at migration. |
| Reassignments | Number of assignee changes since V28. |
| Comments, last 7 days | Comments on the task in the last seven days. |
| Story points | Current estimate; unestimated tasks use 0. |
| Dependency depth | Length of the longest chain of unfinished tasks the task depends on. |
| Remaining sprint days | Calendar days until the sprint ends; 0 once the end has passed. |
| Column WIP | Tasks currently in the same column of the active sprint, including the task itself. |

**Fairness.** No feature identifies a person: assignee, reporter, commenter identity, team membership and similar attributes are deliberately excluded, so the model cannot learn that particular people are "risky". Reassignments and comments are counted, never attributed. A unit test guards the feature record against identity fields. Indirect proxies remain possible (for example, a person who always takes large tasks); risk is a property of the task to be discussed by the team, not a performance measure of individuals.

### Model

- **Standardisation.** Each feature is transformed to `z = (x − mean) / sd` with the mean and population standard deviation of the *training* data only. Constant columns get `sd = 1` (so `z = 0`).
- **Logistic regression.** `p = σ(b + Σ wⱼ zⱼ)`. The parameters minimise the mean log loss plus `(λ/2)‖w‖²` (default `λ = 0.01`; the intercept is not penalised). The optimiser is Newton's method (IRLS) with an Armijo backtracking line search. It stops when the largest absolute gradient component is at most `10⁻⁸`, or after 100 steps. Training uses no randomness and is bit-for-bit reproducible.
- **Cold start.** A model is trained only when both outcomes (late and on time) have at least **80 examples** each: ten events per feature (Peduzzi et al., 1996). Below that threshold a heuristic with fixed expert weights on fixed reference scales is used. Its intercept is calibrated so that the mean predicted probability over the available examples equals the observed late rate, shrunk towards a 25 % prior by a Beta(1, 3) prior: `(late + 1) / (n + 4)`. With no history at all, a typical task gets 25 %. Predictions report which of the two sources produced them.
- **Probabilities** are clamped to `[10⁻⁹, 1 − 10⁻⁹]` so they are always strictly between 0 and 1.

### Explanation

The log-odds are an exact sum of the intercept and one term per feature, `wⱼ · zⱼ` (coefficient × standardised value). A prediction lists the **three terms with the largest absolute value** (ties in feature order; zero terms are omitted). A positive term raises the risk compared with an average task in the training data; a negative term lowers it.

### Evaluation

- **Time-based split.** Examples are sorted by observation time; the earliest share trains, the rest tests. Examples sharing the boundary timestamp all go to the test set, so every training example is strictly older than every test example.
- **ROC AUC** is computed exactly from the Mann–Whitney statistic with mid-ranks. Tied scores count one half, and only the order of the scores matters.
- **Brier score** is the mean squared difference between probability and outcome (0 is perfect; a constant 0.5 scores 0.25).
- **Calibration curve:** equal-width probability bins with count, mean prediction and observed late rate; empty bins are omitted.

### Limitations

- A linear-logistic model cannot represent interactions between features (for example, many points *and* few days left) unless such terms are added explicitly.
- The labels describe history: changes to process, team size or sprint length shift the base rate, which nightly retraining only partly absorbs. The cold-start weights are judgements, not estimates.
- Coefficients are associations, not causes; "top 3 reasons" explain the model, not the task.
- The 80-per-class threshold is a rule of thumb, and λ is not yet chosen by cross-validation. Results on the Jira dataset still have to be reported in `docs/evaluation/`.

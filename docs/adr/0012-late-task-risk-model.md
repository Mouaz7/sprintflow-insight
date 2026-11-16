# ADR 0012: Hand-written regularised logistic regression for late-task risk

Status: accepted for C3 (algorithmic core). Definitions are in [metrics-definition.md](../metrics-definition.md#late-task-risk-c3).

## Context

C3 shows, for each task in the active sprint, the probability that it is not done at sprint end, with the three main reasons. Instances are small, so many have little or no labelled history. The result must be explainable, reproducible with a fixed seed, testable with property and mutation tests, and must not judge individual people.

## Decision

- A new Modulith module `risk` with **no dependencies** on other modules. It contains plain Java only: feature vector, standardisation, training, the cold-start heuristic, explanation and evaluation. Data extraction, nightly training, persistence and the HTTP/UI layer will use it from outside in later steps.
- **L2-regularised logistic regression implemented by hand**, rather than a dependency such as Smile, Tribuo or Weka. The model has eight features: a few hundred lines are fully reviewable, have no transitive dependencies and allow exact tests (recovery of known coefficients, monotone shrinkage, determinism). A library would add a large dependency for a single dot product.
- **Damped Newton (IRLS) instead of plain gradient descent.** The plan called for gradient descent; Newton's method is also a gradient method, using the exact Hessian (a 9×9 Cholesky solve). It converges in a handful of steps without a learning rate to tune. The Armijo line search guarantees a strictly decreasing, strictly convex objective (for λ > 0), so it neither oscillates nor diverges. When the Hessian is not numerically positive definite (only possible with λ = 0), the step falls back to the gradient. Convergence means a largest absolute gradient component ≤ 10⁻⁸; the iteration cap is 100.
- **Standardise with training statistics only**, so the coefficients are comparable and `coefficient × z` is a meaningful explanation. The intercept is not penalised.
- **Cold-start threshold: 80 examples of the rarer outcome** (10 events per feature). Below it, a heuristic with fixed expert weights on reference scales is used. Only its intercept is calibrated, to the Beta(1, 3)-smoothed observed late rate. Both paths share `RiskModel`, so prediction and explanation are identical code. The intercept is found by bisection on the bracket `[logit(t) − max s, logit(t) − min s]`, where `t` is the target rate and `s` are the example scores. This bracket always contains the solution, so there is no fixed bound for it to stop at. If the scores are not finite or the target is still missed by more than 10⁻⁹, a warning is logged and the prior intercept `logit(t)` is used. If the logistic fit does not converge within the iteration cap, the trainer also falls back to the heuristic and logs a warning. The model's `source` then reports `HEURISTIC`.
- **Evaluation utilities** are in the module: exact AUC with mid-ranks, Brier score, reliability bins and a leakage-free time split.
- **Fairness:** no personal identity features; documented in metrics-definition and guarded by a test.

## Consequences

- Determinism and the absence of dependencies make results reproducible; the module is in PIT's target classes.
- The model is linear in the standardised features. Interactions or non-monotone effects need explicit feature engineering or a later model change (for example, gradient-boosted trees), which would require a new explanation method.
- λ = 0.01 and the 80-example threshold are defaults, not tuned values. Time-based cross-validation on the Jira dataset should revisit them when the evaluation is reported in `docs/evaluation/`.
- The cold-start weights are judgements; their direction is tested, their size is not empirically supported until enough history exists.

## Sources

- Peduzzi P. et al. (1996). A simulation study of the number of events per variable in logistic regression analysis. *Journal of Clinical Epidemiology* 49(12).
- Hastie, Tibshirani, Friedman. *The Elements of Statistical Learning*, §4.4 (fitting logistic regression by IRLS).
- Fawcett T. (2006). An introduction to ROC analysis. *Pattern Recognition Letters* 27(8).

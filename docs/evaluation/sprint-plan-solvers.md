# Sprint planning solver benchmark

The existing benchmark generated 64 synthetic backlogs and compared CP-SAT with the greedy baseline. For the single-capacity dependency forests it also ran dynamic programming. The fixture seed is `20261115 + 1000 × size + instance`. The solver uses a fixed search seed and a two-second limit. The [raw CSV](sprint-plan-solvers.csv) records each objective, bound, gap and time.

| Scenario | Stories | Cases | Proven CP-SAT optima | Median CP-SAT time (ms) | CP-SAT improves on greedy |
| --- | ---: | ---: | ---: | ---: | ---: |
| One capacity | 20 | 8 | 8 | 12.7 | 0 |
| One capacity | 50 | 8 | 8 | 16.0 | 2 |
| One capacity | 100 | 8 | 8 | 35.6 | 4 |
| One capacity | 200 | 8 | 8 | 101.0 | 4 |
| Five members | 20 | 8 | 8 | 10.1 | 4 |
| Five members | 50 | 8 | 8 | 17.4 | 4 |
| Five members | 100 | 8 | 8 | 44.9 | 6 |
| Five members | 200 | 8 | 8 | 116.8 | 8 |

CP-SAT matched the dynamic-programming optimum in all 32 supported cases. The synthetic generators exercise the dependency and capacity constraints. Timing depends on the workstation and machine load.

Run from `backend` with Java 25:

```sh
./mvnw test -Dtest=SprintPlanBenchmarkTest -Dtest.excludedGroups=none -Djacoco.skip=true -Dbenchmark.sizes=20,50,100,200 -Dbenchmark.instances=8
```

The test checks feasibility for every plan and writes `target/benchmark/sprint-plan.csv`.

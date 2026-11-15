package com.sprintflowinsight.planning.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.planning.optimizer.RandomBacklogs.Shape;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * RQ3 benchmark: compares the three solvers on reproducible random backlogs and writes {@code
 * target/benchmark/sprint-plan.csv}. Excluded from the default build; run with {@code ./mvnw test
 * -Dtest=SprintPlanBenchmarkTest -Dtest.excludedGroups=none -Djacoco.skip=true}. Override sizes or
 * instances with {@code -Dbenchmark.sizes=20,50} and {@code -Dbenchmark.instances=10}.
 */
@Tag("benchmark")
class SprintPlanBenchmarkTest {
  private static final long SEED = 20_261_115L;

  @Test
  void writeSolverComparison() throws IOException {
    int instances = Integer.getInteger("benchmark.instances", 20);
    List<Integer> sizes =
        List.of(System.getProperty("benchmark.sizes", "20,50,100,200,400").split(",")).stream()
            .map(String::trim)
            .map(Integer::valueOf)
            .toList();
    List<SprintPlanSolver> solvers =
        List.of(
            new CpSatSprintPlanSolver(),
            new GreedySprintPlanSolver(),
            new DynamicProgrammingSprintPlanSolver());
    List<String> rows = new ArrayList<>();
    rows.add("shape,size,instance,seed,solver,status,objective,best_bound,gap,millis");
    for (String shapeName : List.of("single", "members")) {
      for (int size : sizes) {
        for (int instance = 0; instance < instances; instance++) {
          long seed = SEED + 1_000L * size + instance;
          Shape shape =
              shapeName.equals("single")
                  ? new Shape(size, 0, true, false, true)
                  : new Shape(size, 5, false, false, true);
          PlanningProblem problem = RandomBacklogs.generate(seed, shape);
          for (SprintPlanSolver solver : solvers) {
            if (!solver.supports(problem)) {
              continue;
            }
            SprintPlan plan = solver.solve(problem);
            PlanChecks.assertFeasible(problem, plan);
            rows.add(
                String.join(
                    ",",
                    shapeName,
                    Integer.toString(size),
                    Integer.toString(instance),
                    Long.toString(seed),
                    plan.solver().name(),
                    plan.status().name(),
                    Long.toString(plan.objective()),
                    plan.bestBound() == null ? "" : plan.bestBound().toString(),
                    plan.relativeGap() == null
                        ? ""
                        : String.format(Locale.ROOT, "%.6f", plan.relativeGap()),
                    String.format(Locale.ROOT, "%.3f", plan.wallTime().toNanos() / 1e6)));
          }
        }
      }
    }
    Path output = Path.of("target", "benchmark", "sprint-plan.csv");
    Files.createDirectories(output.getParent());
    Files.write(output, rows);
    assertThat(rows).hasSizeGreaterThan(1);
  }
}

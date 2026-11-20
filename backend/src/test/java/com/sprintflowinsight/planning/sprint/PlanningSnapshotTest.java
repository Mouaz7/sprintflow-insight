package com.sprintflowinsight.planning.sprint;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.workspace.time.CapacityUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlanningSnapshotTest {
  @Test
  void hoursRoundUpPerPersonAndMissingEstimatesStayVisible() {
    var effort =
        PlanningSnapshot.Effort.forWork(
            CapacityUnit.HOURS,
            List.of(
                new PlanningSnapshot.Work(1, 1L, null, "To Do", 7L, 31, 0),
                new PlanningSnapshot.Work(2, 1L, null, "To Do", 7L, 30, 0),
                new PlanningSnapshot.Work(3, 1L, null, "To Do", 8L, 1, 0),
                new PlanningSnapshot.Work(4, 1L, null, "To Do", null, 1, 0),
                new PlanningSnapshot.Work(5, 1L, null, "To Do", null, null, 0)),
            0);
    assertThat(effort.complete()).isFalse();
    assertThat(effort.total()).isEqualTo(4);
    assertThat(effort.assigned()).isEqualTo(Map.of(7L, 2, 8L, 1));
  }

  @Test
  void committedBlockerStillRequiresUnfinishedPredecessors() {
    var candidate = new PlanningSnapshot.Work(1, 11L, null, "To Do", null, 60, 1);
    var committed = new PlanningSnapshot.Work(2, null, 7L, "To Do", null, 60, 1);
    var predecessor = new PlanningSnapshot.Work(3, 33L, null, "To Do", null, 60, 1);
    var dependencies =
        PlanningSnapshotService.storyDependencies(
            List.of(new PlanningSnapshotService.Edge(3, 2), new PlanningSnapshotService.Edge(2, 1)),
            Map.of(1L, candidate, 2L, committed, 3L, predecessor),
            Set.of(2L));
    assertThat(dependencies.get(11L)).containsExactly("33");
  }
}

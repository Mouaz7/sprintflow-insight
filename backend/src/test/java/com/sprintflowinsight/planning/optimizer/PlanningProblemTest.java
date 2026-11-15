package com.sprintflowinsight.planning.optimizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlanningProblemTest {

  @Test
  void storiesNormaliseDependenciesAndEffort() {
    var story =
        new CandidateStory(
            "s", 3, 2, Map.of("bo", 1, "al", 2), List.of("z", "a", "z"), Lock.NONE);
    assertThat(story.predecessorIds()).containsExactly("a", "z");
    assertThat(story.effortByMember().keySet()).containsExactly("al", "bo");
    var bare = new CandidateStory("s", 0, 0, null, null, Lock.NONE);
    assertThat(bare.effortByMember()).isEmpty();
    assertThat(bare.predecessorIds()).isEmpty();
    assertThat(CandidateStory.of("s", CandidateStory.MAX_VALUE, CandidateStory.MAX_POINTS).value())
        .isEqualTo(CandidateStory.MAX_VALUE);
    assertThat(bare.withLock(Lock.LOCKED_IN).lock()).isEqualTo(Lock.LOCKED_IN);
    assertThat(bare.withPredecessors("p").predecessorIds()).containsExactly("p");
    assertThat(bare.withEffort(Map.of("al", 1)).effortByMember()).containsEntry("al", 1);
  }

  @Test
  void invalidStoriesAreRejected() {
    assertThatIllegalArgumentException().isThrownBy(() -> CandidateStory.of(" ", 1, 1));
    assertThatIllegalArgumentException().isThrownBy(() -> CandidateStory.of(null, 1, 1));
    assertThatIllegalArgumentException().isThrownBy(() -> CandidateStory.of("s", -1, 1));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> CandidateStory.of("s", CandidateStory.MAX_VALUE + 1, 1));
    assertThatIllegalArgumentException().isThrownBy(() -> CandidateStory.of("s", 1, -1));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> CandidateStory.of("s", 1, CandidateStory.MAX_POINTS + 1));
    assertThatNullPointerException()
        .isThrownBy(() -> new CandidateStory("s", 1, 1, Map.of(), List.of(), null));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> CandidateStory.of("s", 1, 1).withEffort(Map.of(" ", 1)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> CandidateStory.of("s", 1, 1).withEffort(Map.of("al", -1)));
    var nullEffort = new HashMap<String, Integer>();
    nullEffort.put("al", null);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> CandidateStory.of("s", 1, 1).withEffort(nullEffort));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> CandidateStory.of("s", 1, 1).withPredecessors("s"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> CandidateStory.of("s", 1, 1).withPredecessors(" "));
  }

  @Test
  void invalidProblemsAreRejected() {
    var a = CandidateStory.of("a", 1, 1);
    assertThatIllegalArgumentException().isThrownBy(() -> PlanningProblem.of(-1, List.of(a)));
    assertThatIllegalArgumentException().isThrownBy(() -> PlanningProblem.of(5, List.of(a, a)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new PlanningProblem(List.of(a), Set.of("a"), 5, Map.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new PlanningProblem(List.of(a), Set.of(), 5, Map.of("al", -1)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new PlanningProblem(List.of(a), Set.of(), 5, Map.of(" ", 1)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> PlanningProblem.of(5, List.of(a.withEffort(Map.of("al", 1)))))
        .withMessageContaining("unknown member");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                PlanningProblem.of(
                    5,
                    List.of(
                        a.withPredecessors("c"),
                        CandidateStory.of("b", 1, 1).withPredecessors("a"),
                        CandidateStory.of("c", 1, 1).withPredecessors("b"))))
        .withMessageContaining("cycle");
  }

  @Test
  void problemsKeepBacklogOrderAndSortMembers() {
    var problem =
        new PlanningProblem(
            List.of(CandidateStory.of("b", 1, 1), CandidateStory.of("a", 1, 1)),
            Set.of("x"),
            0,
            Map.of("zed", 0, "al", 3));
    assertThat(problem.stories()).extracting(CandidateStory::id).containsExactly("b", "a");
    assertThat(problem.memberCapacities().keySet()).containsExactly("al", "zed");
    assertThat(problem.hasMemberCapacities()).isTrue();
    assertThat(PlanningProblem.of(0, List.of()).hasMemberCapacities()).isFalse();
  }

  @Test
  void topologicalOrderPutsPredecessorsFirstAndKeepsBacklogOrderOtherwise() {
    var stories =
        List.of(
            CandidateStory.of("a", 1, 1).withPredecessors("c"),
            CandidateStory.of("b", 1, 1),
            CandidateStory.of("c", 1, 1).withPredecessors("done"));
    var ids = Map.of("a", 0, "b", 1, "c", 2);
    assertThat(PlanningProblem.topologicalOrder(stories, ids)).containsExactly(1, 2, 0);
  }

  @Test
  void settingsAndGapsAreValidated() {
    assertThat(CpSatSettings.defaults())
        .isEqualTo(new CpSatSettings(Duration.ofSeconds(2), 8, 0, false));
    assertThat(CpSatSettings.defaults().withTimeLimit(Duration.ofMillis(5)).timeLimit())
        .isEqualTo(Duration.ofMillis(5));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CpSatSettings(Duration.ZERO, 1, 0, false));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CpSatSettings(Duration.ofSeconds(-1), 1, 0, false));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CpSatSettings(Duration.ofSeconds(1), 0, 0, false));
    assertThat(new CpSatSettings(Duration.ofSeconds(1), 1, 0, false).workers()).isOne();
    assertThat(SprintPlan.gap(0, 0)).isZero();
    assertThat(SprintPlan.gap(8, 10)).isEqualTo(0.2);
    assertThat(SprintPlan.gap(10, 10)).isZero();
  }
}

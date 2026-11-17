package com.sprintflowinsight.planning;

import com.sprintflowinsight.planning.optimizer.CandidateStory;
import com.sprintflowinsight.planning.optimizer.Lock;
import com.sprintflowinsight.workspace.roadmap.PlanningBacklog.PlanningStory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Maps the B3 backlog view to optimiser input: {@code business_value} becomes the objective
 * weight, story points the capacity cost, and story precedences (lifted from task dependencies)
 * the predecessors. Done stories satisfy dependencies instead of being candidates.
 *
 * @param candidates unfinished stories in backlog order
 * @param doneStoryIds finished stories
 */
public record BacklogCandidates(List<CandidateStory> candidates, Set<String> doneStoryIds) {
  /**
   * Value used for stories the team has not valued yet: the lowest point on the 1–5 scale, so an
   * unvalued story is still worth planning but never beats a valued one of the same size.
   */
  public static final long UNVALUED = 1;

  public static BacklogCandidates of(Collection<PlanningStory> backlog) {
    List<CandidateStory> candidates = new ArrayList<>();
    Set<String> done = new TreeSet<>();
    for (PlanningStory story : backlog) {
      String id = story.id().toString();
      if (story.done()) {
        done.add(id);
        continue;
      }
      long value = story.businessValue() == null ? UNVALUED : story.businessValue();
      List<String> predecessors =
          story.predecessorIds().stream().sorted().map(Object::toString).toList();
      candidates.add(
          new CandidateStory(id, value, Math.max(0, story.points()), Map.of(), predecessors, Lock.NONE));
    }
    return new BacklogCandidates(List.copyOf(candidates), Set.copyOf(done));
  }
}

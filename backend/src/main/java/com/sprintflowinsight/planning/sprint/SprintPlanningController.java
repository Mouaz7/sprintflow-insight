package com.sprintflowinsight.planning.sprint;

import com.sprintflowinsight.planning.sprint.SprintPlanningViews.Options;
import com.sprintflowinsight.planning.sprint.SprintPlanningViews.Proposal;
import com.sprintflowinsight.workspace.time.CapacityUnit;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/sprint-plan")
public class SprintPlanningController {
  private final PlanningSnapshotService snapshots;
  private final SprintPlanningService planner;

  SprintPlanningController(PlanningSnapshotService snapshots, SprintPlanningService planner) {
    this.snapshots = snapshots;
    this.planner = planner;
  }

  @GetMapping("/options")
  public Options options(
      @PathVariable long projectId,
      @RequestParam long sprintId,
      @RequestParam(defaultValue = "POINTS") CapacityUnit unit,
      HttpSession session) {
    return snapshots.load(projectId, sprintId, unit, session).options();
  }

  @PostMapping("/optimize")
  public Proposal optimize(
      @PathVariable long projectId,
      @Valid @RequestBody SprintPlanRequest request,
      HttpSession session) {
    return planner.optimize(projectId, request, session);
  }
}

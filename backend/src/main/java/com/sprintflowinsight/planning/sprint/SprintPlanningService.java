package com.sprintflowinsight.planning.sprint;

import com.sprintflowinsight.planning.optimizer.CpSatSprintPlanSolver;
import com.sprintflowinsight.planning.sprint.SprintPlanningViews.Proposal;
import jakarta.servlet.http.HttpSession;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class SprintPlanningService {
  private final PlanningSnapshotService snapshots;
  private final Semaphore open = new Semaphore(2);

  SprintPlanningService(PlanningSnapshotService snapshots) {
    this.snapshots = snapshots;
  }

  Proposal optimize(long projectId, SprintPlanRequest request, HttpSession session) {
    var snapshot = snapshots.load(projectId, request.sprintId(), request.unit(), session);
    var problem = snapshot.problem(request);
    if (!open.tryAcquire())
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Sprint planning is busy. Try again shortly.");
    try {
      // The project snapshot transaction is closed before invoking CP-SAT.
      return new Proposal(snapshot.options(), new CpSatSprintPlanSolver().solve(problem));
    } finally {
      open.release();
    }
  }
}

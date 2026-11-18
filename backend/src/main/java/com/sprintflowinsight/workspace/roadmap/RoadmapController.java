package com.sprintflowinsight.workspace.roadmap;

import com.sprintflowinsight.workspace.roadmap.RoadmapViews.DependencyOverview;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.DependencyView;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.EstimateView;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.Roadmap;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class RoadmapController {
  private final RoadmapService roadmap;
  private final DependencyService dependencies;

  public RoadmapController(RoadmapService roadmap, DependencyService dependencies) {
    this.roadmap = roadmap;
    this.dependencies = dependencies;
  }

  @GetMapping("/roadmap")
  public Roadmap roadmap(@PathVariable Long projectId, HttpSession session) {
    return roadmap.roadmap(projectId, session);
  }

  @GetMapping("/dependencies")
  public DependencyOverview dependencies(@PathVariable Long projectId, HttpSession session) {
    return roadmap.dependencies(projectId, session);
  }

  @PostMapping("/dependencies")
  public ResponseEntity<DependencyView> add(
      @PathVariable Long projectId,
      @Valid @RequestBody DependencyRequest request,
      HttpSession session) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(dependencies.add(projectId, request, session));
  }

  @DeleteMapping("/dependencies/{dependencyId}")
  public ResponseEntity<Void> remove(
      @PathVariable Long projectId, @PathVariable Long dependencyId, HttpSession session) {
    dependencies.remove(projectId, dependencyId, session);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/estimates")
  public List<EstimateView> estimates(@PathVariable Long projectId, HttpSession session) {
    return roadmap.estimates(projectId, session);
  }

  @PutMapping("/tasks/{taskId}/estimate")
  public EstimateView estimate(
      @PathVariable Long projectId,
      @PathVariable Long taskId,
      @Valid @RequestBody EstimateRequest request,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    return dependencies.estimate(projectId, taskId, request, ifMatch, session);
  }

  @DeleteMapping("/tasks/{taskId}/estimate")
  public ResponseEntity<Void> clearEstimate(
      @PathVariable Long projectId,
      @PathVariable Long taskId,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    dependencies.clearEstimate(projectId, taskId, ifMatch, session);
    return ResponseEntity.noContent().build();
  }
}

package com.sprintflowinsight.workspace.roadmap;

import com.sprintflowinsight.workspace.roadmap.RoadmapViews.EpicView;
import com.sprintflowinsight.workspace.roadmap.RoadmapViews.StoryEpicView;
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
public class EpicController {
  private final EpicService service;

  public EpicController(EpicService service) {
    this.service = service;
  }

  @GetMapping("/epics")
  public List<EpicView> list(@PathVariable Long projectId, HttpSession session) {
    return service.list(projectId, session);
  }

  @PostMapping("/epics")
  public ResponseEntity<EpicView> create(
      @PathVariable Long projectId, @Valid @RequestBody EpicRequest request, HttpSession session) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(service.create(projectId, request, session));
  }

  @PutMapping("/epics/{epicId}")
  public EpicView update(
      @PathVariable Long projectId,
      @PathVariable Long epicId,
      @Valid @RequestBody EpicRequest request,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    return service.update(projectId, epicId, request, ifMatch, session);
  }

  @DeleteMapping("/epics/{epicId}")
  public ResponseEntity<Void> delete(
      @PathVariable Long projectId,
      @PathVariable Long epicId,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    service.delete(projectId, epicId, ifMatch, session);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/stories/{storyId}/epic")
  public StoryEpicView assign(
      @PathVariable Long projectId,
      @PathVariable Long storyId,
      @Valid @RequestBody StoryEpicRequest request,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    return service.assign(projectId, storyId, request, ifMatch, session);
  }
}

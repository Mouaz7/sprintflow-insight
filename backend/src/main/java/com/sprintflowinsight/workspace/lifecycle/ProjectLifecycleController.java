package com.sprintflowinsight.workspace.lifecycle;

import com.sprintflowinsight.workspace.project.Project;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ProjectLifecycleController {
  private final ProjectLifecycleService service;

  public ProjectLifecycleController(ProjectLifecycleService service) {
    this.service = service;
  }

  @PutMapping("/projects/{id}/archive")
  public Project archive(
      @PathVariable Long id,
      @jakarta.validation.Valid @RequestBody ArchiveRequest body,
      HttpSession session) {
    return service.archive(id, body, session);
  }

  @GetMapping("/workspace/trash")
  public List<TrashItem> trash(HttpSession session) {
    return service.trash(session);
  }

  @PutMapping("/workspace/trash/{kind}/{id}")
  public void change(
      @PathVariable String kind,
      @PathVariable Long id,
      @jakarta.validation.Valid @RequestBody TrashRequest body,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    service.change(kind, id, body, ifMatch, session);
  }

  @DeleteMapping("/workspace/trash/{kind}/{id}")
  public void purge(
      @PathVariable String kind,
      @PathVariable Long id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    service.purge(kind, id, ifMatch, session);
  }
}

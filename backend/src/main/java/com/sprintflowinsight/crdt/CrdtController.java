package com.sprintflowinsight.crdt;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/collaboration/{target}/{id}")
class CrdtController {
  private final CrdtService collaboration;

  CrdtController(CrdtService collaboration) {
    this.collaboration = collaboration;
  }

  record Projection(@Positive long revision, @NotNull @Size(max = 1000) String text) {}

  @GetMapping
  CrdtService.Snapshot read(
      @PathVariable long projectId,
      @PathVariable CrdtTarget target,
      @PathVariable long id,
      @RequestParam(defaultValue = "0") @PositiveOrZero long after,
      HttpSession session) {
    return collaboration.read(projectId, target, id, after, session);
  }

  @GetMapping("/metadata")
  CrdtService.Metadata metadata(
      @PathVariable long projectId,
      @PathVariable CrdtTarget target,
      @PathVariable long id,
      HttpSession session) {
    return collaboration.metadata(projectId, target, id, session);
  }

  @PostMapping
  CrdtService.Snapshot activate(
      @PathVariable long projectId,
      @PathVariable CrdtTarget target,
      @PathVariable long id,
      HttpSession session) {
    return collaboration.activate(projectId, target, id, session);
  }

  @PostMapping("/projection")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void project(
      @PathVariable long projectId,
      @PathVariable CrdtTarget target,
      @PathVariable long id,
      @Valid @RequestBody Projection projection,
      HttpSession session) {
    collaboration.project(projectId, target, id, projection.revision(), projection.text(), session);
  }
}

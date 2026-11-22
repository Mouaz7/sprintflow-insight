package com.sprintflowinsight.workspace.offline;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/offline-operations")
public class OfflineOperationController {
  private final OfflineOperationService operations;

  OfflineOperationController(OfflineOperationService operations) {
    this.operations = operations;
  }

  public record Result(long taskVersion) {}

  @PostMapping
  public Result replay(
      @PathVariable long projectId,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      @Valid @RequestBody OfflineOperationRequest request,
      HttpSession session) {
    return operations.replay(projectId, request, ifMatch, session);
  }
}

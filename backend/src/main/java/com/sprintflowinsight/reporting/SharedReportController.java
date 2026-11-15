package com.sprintflowinsight.reporting;

import jakarta.servlet.http.HttpSession;
import java.security.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SharedReportController {
  private final SharedReportService service;

  public SharedReportController(SharedReportService service) {
    this.service = service;
  }

  @PostMapping("/projects/{projectId}/reports")
  public Map<String, Object> create(
      @PathVariable Long projectId,
      @jakarta.validation.Valid @RequestBody ReportRequest request,
      HttpSession session)
      throws Exception {
    return service.create(projectId, request, session);
  }

  @GetMapping("/projects/{projectId}/reports")
  public List<Map<String, Object>> list(@PathVariable Long projectId, HttpSession session) {
    return service.list(projectId, session);
  }

  @DeleteMapping("/projects/{projectId}/reports/{id}")
  public void revoke(@PathVariable Long projectId, @PathVariable Long id, HttpSession session) {
    service.revoke(projectId, id, session);
  }

  @GetMapping("/shared-reports/{token}")
  public Object read(@PathVariable String token) throws Exception {
    return service.read(token);
  }
}

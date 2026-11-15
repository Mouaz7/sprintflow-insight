package com.sprintflowinsight.reporting;

import com.sprintflowinsight.workspace.activity.ActivityEvent;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class MetricsController {
  private final MetricsExportService service;

  public MetricsController(MetricsExportService service) {
    this.service = service;
  }

  @GetMapping("/metrics")
  public ResponseEntity<?> metrics(
      @PathVariable Long projectId,
      @RequestParam(required = false) Long sprintId,
      HttpSession session) {
    return service.metrics(projectId, sprintId, session);
  }

  @GetMapping("/activity")
  public ResponseEntity<List<ActivityEvent>> activity(
      @PathVariable Long projectId, HttpSession session) {
    return service.activity(projectId, session);
  }

  @DeleteMapping("/activity")
  public ResponseEntity<?> clearActivity(@PathVariable Long projectId, HttpSession session) {
    return service.clearActivity(projectId, session);
  }

  @PutMapping("/activity/{activityId}")
  public ResponseEntity<?> updateActivity(
      @PathVariable Long projectId,
      @PathVariable Long activityId,
      @Valid @org.springframework.web.bind.annotation.RequestBody ActivityUpdateRequest request,
      HttpSession session) {
    return service.updateActivity(projectId, activityId, request, session);
  }

  @DeleteMapping("/activity/{activityId}")
  public ResponseEntity<?> deleteActivity(
      @PathVariable Long projectId, @PathVariable Long activityId, HttpSession session) {
    return service.deleteActivity(projectId, activityId, session);
  }

  @GetMapping(value = "/export.csv", produces = "text/csv")
  public void exportCsv(
      @PathVariable Long projectId,
      @RequestParam(required = false) Long sprintId,
      @Valid @org.springframework.web.bind.annotation.ModelAttribute
          com.sprintflowinsight.reporting.TaskExportFilter filters,
      HttpSession session,
      HttpServletResponse response)
      throws IOException {
    service.exportCsv(projectId, sprintId, filters, session, response);
  }

  @GetMapping(value = "/export.pdf", produces = "application/pdf")
  public void exportPdf(
      @PathVariable Long projectId,
      @RequestParam(required = false) Long sprintId,
      @Valid @org.springframework.web.bind.annotation.ModelAttribute
          com.sprintflowinsight.reporting.TaskExportFilter filters,
      HttpSession session,
      HttpServletResponse response)
      throws IOException {
    service.exportPdf(projectId, sprintId, filters, session, response);
  }
}

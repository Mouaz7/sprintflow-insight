package com.sprintflowinsight.reporting;

import tools.jackson.databind.ObjectMapper;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectRepository;
import com.sprintflowinsight.workspace.sprint.SprintRepository;
import jakarta.servlet.http.HttpSession;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@org.springframework.stereotype.Service
public class SharedReportService {
  private final SharedReportRepository reports;
  private final ProjectRepository projects;
  private final ProjectAccessService access;
  private final MetricsService metrics;
  private final ObjectMapper json;
  private final SprintRepository sprints;

  public SharedReportService(
      SharedReportRepository reports,
      ProjectRepository projects,
      ProjectAccessService access,
      MetricsService metrics,
      ObjectMapper json,
      SprintRepository sprints) {
    this.reports = reports;
    this.projects = projects;
    this.access = access;
    this.metrics = metrics;
    this.json = json;
    this.sprints = sprints;
  }

  public Map<String, Object> create(Long projectId, ReportRequest request, HttpSession session)
      throws Exception {
    access.adminOrOwner(projectId, session);
    Long sprintId = request.sprintId();
    if (sprintId != null
        && sprints
            .findById(sprintId)
            .filter(s -> s.getProjectId().equals(projectId) && s.getTrashedAt() == null)
            .isEmpty())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a sprint in this project.");
    var project = projects.findById(projectId).orElseThrow();
    var data = metrics.summary(projectId, sprintId);
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("projectName", project.getName());
    snapshot.put("description", project.getDescription());
    snapshot.put("generatedAt", Instant.now().toString());
    snapshot.put(
        "sprintName", sprintId == null ? "" : sprints.findById(sprintId).orElseThrow().getName());
    for (String key :
        List.of(
            "taskCount",
            "completionRate",
            "velocityStoryPoints",
            "throughput",
            "cycleTimeHours",
            "leadTimeHours",
            "throughputLast14Days",
            "statusCounts",
            "burndown",
            "burndownAvailable")) if (data.containsKey(key)) snapshot.put(key, data.get(key));
    String token = com.sprintflowinsight.common.SecureTokens.token();
    var report = new SharedReport();
    report.setProjectId(projectId);
    report.setTokenHash(hash(token));
    report.setSnapshot(json.writeValueAsString(snapshot));
    report.setCreatedAt(Instant.now());
    report.setExpiresAt(Instant.now().plusSeconds(7 * 86400));
    report.setCreatedBy(access.email(session));
    reports.save(report);
    return Map.of(
        "id", report.getId(), "path", "/?report=" + token, "expiresAt", report.getExpiresAt());
  }

  public List<Map<String, Object>> list(Long projectId, HttpSession session) {
    // Listing and revoking stay possible on archived projects: they only reduce exposure.
    access.require(
        projectId,
        session,
        com.sprintflowinsight.workspace.project.AccessContext.Need.MANAGE_ARCHIVED);
    return reports.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
        .map(
            r ->
                Map.<String, Object>of(
                    "id", r.getId(), "createdAt", r.getCreatedAt(), "expiresAt", r.getExpiresAt()))
        .toList();
  }

  public void revoke(Long projectId, Long id, HttpSession session) {
    access.require(
        projectId,
        session,
        com.sprintflowinsight.workspace.project.AccessContext.Need.MANAGE_ARCHIVED);
    var report =
        reports
            .findById(id)
            .filter(r -> r.getProjectId().equals(projectId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    reports.delete(report);
  }

  public Object read(String token) throws Exception {
    if (!token.matches("[A-Za-z0-9_-]{43}"))
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "This report link is invalid or has expired.");
    var report =
        reports
            .findByTokenHash(hash(token))
            .filter(r -> r.getExpiresAt().isAfter(Instant.now()))
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "This report link is invalid or has expired."));
    if (projects.findById(report.getProjectId()).filter(p -> p.getTrashedAt() == null).isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return json.readTree(report.getSnapshot());
  }

  private String hash(String value) {
    return com.sprintflowinsight.common.SecureTokens.sha256(value);
  }
}

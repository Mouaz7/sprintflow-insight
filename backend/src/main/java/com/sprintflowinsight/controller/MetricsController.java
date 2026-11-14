package com.sprintflowinsight.controller;

import com.sprintflowinsight.model.ActivityEvent;
import com.sprintflowinsight.model.Task;
import com.sprintflowinsight.project.Project;
import com.sprintflowinsight.project.ProjectAccessService;
import com.sprintflowinsight.project.ProjectRepository;
import com.sprintflowinsight.repository.TaskRepository;
import com.sprintflowinsight.service.PdfReportWriter;
import com.sprintflowinsight.service.ActivityService;
import com.sprintflowinsight.service.MetricsService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
  private static final DateTimeFormatter EXPORT_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")
          .withLocale(Locale.UK)
          .withZone(ZoneId.systemDefault());
  private final ProjectRepository projects;
  private final MetricsService metrics;
  private final ActivityService activity;
  private final TaskRepository tasks;
  private final ProjectAccessService access;
  private final PdfReportWriter pdfWriter;

  public MetricsController(
      ProjectRepository projects,
      MetricsService metrics,
      ActivityService activity,
      TaskRepository tasks,
      ProjectAccessService access,
      PdfReportWriter pdfWriter) {
    this.pdfWriter = pdfWriter;
    this.projects = projects;
    this.metrics = metrics;
    this.activity = activity;
    this.tasks = tasks;
    this.access = access;
  }

  @GetMapping("/metrics")
  public ResponseEntity<?> metrics(
      @PathVariable Long projectId,
      @RequestParam(required = false) Long sprintId,
      HttpSession session) {
    access.read(projectId, session);
    return ResponseEntity.ok(metrics.summary(projectId, sprintId));
  }

  @GetMapping("/activity")
  public ResponseEntity<List<ActivityEvent>> activity(
      @PathVariable Long projectId, HttpSession session) {
    access.read(projectId, session);
    return ResponseEntity.ok(activity.forProject(projectId));
  }

  @DeleteMapping("/activity")
  public ResponseEntity<?> clearActivity(@PathVariable Long projectId, HttpSession session) {
    access.adminOrOwner(projectId, session);
    activity.clearProject(projectId);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/activity/{activityId}")
  public ResponseEntity<?> updateActivity(
      @PathVariable Long projectId,
      @PathVariable Long activityId,
      @Valid @org.springframework.web.bind.annotation.RequestBody ActivityUpdateRequest request,
      HttpSession session) {
    access.adminOrOwner(projectId, session);
    return ResponseEntity.ok(activity.updateSummary(projectId, activityId, request.summary()));
  }

  @DeleteMapping("/activity/{activityId}")
  public ResponseEntity<?> deleteActivity(
      @PathVariable Long projectId, @PathVariable Long activityId, HttpSession session) {
    access.adminOrOwner(projectId, session);
    activity.deleteEvent(projectId, activityId);
    return ResponseEntity.noContent().build();
  }

  public record ActivityUpdateRequest(
      @NotBlank(message = "Update summary is required.")
          @Size(max = 500, message = "Update summary must be 500 characters or fewer.")
          String summary) {}

  @GetMapping(value = "/export.csv", produces = "text/csv")
  public void exportCsv(
      @PathVariable Long projectId,
      @RequestParam(required = false) Long sprintId,
      @Valid @org.springframework.web.bind.annotation.ModelAttribute
          com.sprintflowinsight.service.TaskExportFilter filters,
      HttpSession session,
      HttpServletResponse response)
      throws IOException {
    access.read(projectId, session);
    Project project = projects.findById(projectId).orElseThrow();
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType("text/csv; charset=UTF-8");
    response.setHeader(
        "Content-Disposition", "attachment; filename=sprintflow-" + projectId + ".csv");
    StringBuilder csv =
        new StringBuilder("Project,")
            .append(q(project.getName()))
            .append('\n')
            .append("Generated,")
            .append(q(EXPORT_TIME.format(Instant.now())))
            .append('\n')
            .append("Scope,")
            .append(q(sprintId == null ? "All sprints" : "Sprint " + sprintId))
            .append('\n')
            .append("Filters,")
            .append(q(filters.description()))
            .append('\n')
            .append("\nTitle,Status,Sprint,Points,Due date,Blocked\n");
    for (Task task : scopedTasks(projectId, sprintId, filters)) {
      csv.append(q(task.getTitle()))
          .append(',')
          .append(q(task.getStatus()))
          .append(',')
          .append(task.getSprintId() == null ? "" : task.getSprintId())
          .append(',')
          .append(task.getStoryPoints())
          .append(',')
          .append(task.getDueAt() == null ? "" : EXPORT_TIME.format(task.getDueAt()))
          .append(',')
          .append(task.isBlocked())
          .append('\n');
    }
    response.getWriter().write(csv.toString());
  }

  @GetMapping(value = "/export.pdf", produces = "application/pdf")
  public void exportPdf(
      @PathVariable Long projectId,
      @RequestParam(required = false) Long sprintId,
      @Valid @org.springframework.web.bind.annotation.ModelAttribute
          com.sprintflowinsight.service.TaskExportFilter filters,
      HttpSession session,
      HttpServletResponse response)
      throws IOException {
    access.read(projectId, session);
    Project project = projects.findById(projectId).orElseThrow();
    List<Task> selectedTasks = scopedTasks(projectId, sprintId, filters);

    long completed = selectedTasks.stream().filter(task -> "Done".equals(task.getStatus())).count();
    long completedPoints =
        selectedTasks.stream()
            .filter(task -> "Done".equals(task.getStatus()))
            .mapToLong(Task::getStoryPoints)
            .sum();
    List<PdfReportWriter.Line> lines = new ArrayList<>();
    lines.add(PdfReportWriter.Line.title("SprintFlow Insight"));
    lines.add(PdfReportWriter.Line.body("Project: " + project.getName()));
    lines.add(PdfReportWriter.Line.body("Generated: " + EXPORT_TIME.format(Instant.now())));
    lines.add(
        PdfReportWriter.Line.body(
            "Scope: " + (sprintId == null ? "All sprints" : "Sprint " + sprintId)));
    lines.add(PdfReportWriter.Line.body("Filters: " + filters.description()));
    lines.add(PdfReportWriter.Line.heading("DELIVERY SUMMARY"));
    lines.add(
        PdfReportWriter.Line.body(
            "Selected tasks: " + selectedTasks.size() + "   |   Completed tasks: " + completed));
    lines.add(
        PdfReportWriter.Line.body(
            "Completed points: "
                + completedPoints
                + "   |   Open work: "
                + (selectedTasks.size() - completed)));
    lines.add(
        PdfReportWriter.Line.body(
            "Blocked tasks: " + selectedTasks.stream().filter(Task::isBlocked).count()));
    lines.add(PdfReportWriter.Line.heading("TASKS"));
    if (selectedTasks.isEmpty()) lines.add(PdfReportWriter.Line.body("No tasks match this export."));
    for (Task task : selectedTasks)
      lines.add(
          PdfReportWriter.Line.body(
              task.getTitle()
                  + " — "
                  + task.getStatus()
                  + " — "
                  + task.getStoryPoints()
                  + " pts"
                  + (task.getDueAt() == null ? "" : " — due " + EXPORT_TIME.format(task.getDueAt()))
                  + (task.isBlocked() ? " — BLOCKED" : "")));
    byte[] pdf = pdfWriter.render("SprintFlow Insight — " + project.getName(), lines);
    response.setContentType("application/pdf");
    response.setHeader(
        "Content-Disposition", "attachment; filename=sprintflow-" + projectId + ".pdf");
    response.setContentLength(pdf.length);
    response.getOutputStream().write(pdf);
  }

  private List<Task> scopedTasks(
      Long projectId, Long sprintId, com.sprintflowinsight.service.TaskExportFilter filters) {
    Instant now = Instant.now();
    return tasks.findByProjectId(projectId).stream()
        .filter(task -> sprintId == null || sprintId.equals(task.getSprintId()))
        .filter(task -> filters.matches(task, now))
        .toList();
  }




  private String q(String value) {
    return com.sprintflowinsight.CsvCell.quote(value);
  }
}

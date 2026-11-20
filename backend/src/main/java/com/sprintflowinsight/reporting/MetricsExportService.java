package com.sprintflowinsight.reporting;

import com.sprintflowinsight.workspace.activity.ActivityEvent;
import com.sprintflowinsight.workspace.activity.ActivityService;
import com.sprintflowinsight.workspace.customfield.CustomFieldService;
import com.sprintflowinsight.workspace.customfield.CustomFieldViews;
import com.sprintflowinsight.workspace.project.Project;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectRepository;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.ResponseEntity;

@org.springframework.stereotype.Service
public class MetricsExportService {
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
  private final CustomFieldService customFields;
  private final com.sprintflowinsight.workspace.project.ProjectAdministrativeAccess admin;

  public MetricsExportService(
      ProjectRepository projects,
      MetricsService metrics,
      ActivityService activity,
      TaskRepository tasks,
      ProjectAccessService access,
      PdfReportWriter pdfWriter,
      CustomFieldService customFields,
      com.sprintflowinsight.workspace.project.ProjectAdministrativeAccess admin) {
    this.admin = admin;
    this.customFields = customFields;
    this.pdfWriter = pdfWriter;
    this.projects = projects;
    this.metrics = metrics;
    this.activity = activity;
    this.tasks = tasks;
    this.access = access;
  }

  public ResponseEntity<?> metrics(Long projectId, Long sprintId, HttpSession session) {
    access.read(projectId, session);
    return ResponseEntity.ok(metrics.summary(projectId, sprintId));
  }

  public ResponseEntity<List<ActivityEvent>> activity(Long projectId, HttpSession session) {
    access.read(projectId, session);
    return ResponseEntity.ok(activity.forProject(projectId));
  }

  @org.springframework.transaction.annotation.Transactional
  public ResponseEntity<?> clearActivity(Long projectId, HttpSession session) {
    var caller =
        admin.lock(
            projectId, session, com.sprintflowinsight.workspace.project.AccessContext.Need.MANAGE);
    admin.record(caller, "ACTIVITY_CLEARED", "activity", projectId, java.util.Map.of());
    activity.clearProject(projectId);
    return ResponseEntity.noContent().build();
  }

  @org.springframework.transaction.annotation.Transactional
  public ResponseEntity<?> updateActivity(
      Long projectId, Long activityId, ActivityUpdateRequest request, HttpSession session) {
    var caller =
        admin.lock(
            projectId, session, com.sprintflowinsight.workspace.project.AccessContext.Need.MANAGE);
    admin.record(caller, "ACTIVITY_UPDATED", "activity", activityId, java.util.Map.of());
    return ResponseEntity.ok(activity.updateSummary(projectId, activityId, request.summary()));
  }

  @org.springframework.transaction.annotation.Transactional
  public ResponseEntity<?> deleteActivity(Long projectId, Long activityId, HttpSession session) {
    var caller =
        admin.lock(
            projectId, session, com.sprintflowinsight.workspace.project.AccessContext.Need.MANAGE);
    admin.record(caller, "ACTIVITY_DELETED", "activity", activityId, java.util.Map.of());
    activity.deleteEvent(projectId, activityId);
    return ResponseEntity.noContent().build();
  }

  @org.springframework.transaction.annotation.Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public void exportCsv(
      Long projectId,
      Long sprintId,
      com.sprintflowinsight.reporting.TaskExportFilter filters,
      HttpSession session,
      HttpServletResponse response)
      throws IOException {
    access.read(projectId, session);
    Project project = projects.findById(projectId).orElseThrow();
    var selected =
        scopedTasks(projectId, sprintId, filters, session).stream()
            .sorted(java.util.Comparator.comparing(Task::getId))
            .toList();
    var definitions = customFields.definitions(projectId, session);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType("text/csv; charset=UTF-8");
    response.setHeader(
        "Content-Disposition", "attachment; filename=sprintflow-" + projectId + ".csv");
    StringBuilder header =
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
            .append("\nTitle,Status,Sprint,Points,Due date,Blocked");
    definitions.forEach(field -> header.append(',').append(q("Custom: " + field.name())));
    header.append('\n');
    var writer = response.getWriter();
    try (var rows =
        definitions.isEmpty()
            ? java.util.stream.Stream.<CustomFieldViews.TaskValueRow>empty()
            : customFields.streamForExport(projectId, session)) {
      var iterator = rows.iterator();
      var next = iterator.hasNext() ? iterator.next() : null;
      writer.write(header.toString());
      for (Task task : selected) {
        java.util.Map<Long, String> cells = new java.util.HashMap<>();
        while (next != null && next.taskId() <= task.getId()) {
          if (next.taskId().equals(task.getId()))
            cells.put(next.value().fieldId(), next.value().displayValue());
          next = iterator.hasNext() ? iterator.next() : null;
        }
        StringBuilder line = new StringBuilder();
        line.append(q(task.getTitle()))
            .append(',')
            .append(q(task.getStatus()))
            .append(',')
            .append(task.getSprintId() == null ? "" : task.getSprintId())
            .append(',')
            .append(task.getStoryPoints())
            .append(',')
            .append(task.getDueAt() == null ? "" : EXPORT_TIME.format(task.getDueAt()))
            .append(',')
            .append(task.isBlocked());
        definitions.forEach(
            field -> line.append(',').append(q(cells.getOrDefault(field.id(), ""))));
        writer.write(line.append('\n').toString());
      }
    }
  }

  @org.springframework.transaction.annotation.Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  public void exportPdf(
      Long projectId,
      Long sprintId,
      com.sprintflowinsight.reporting.TaskExportFilter filters,
      HttpSession session,
      HttpServletResponse response)
      throws IOException {
    access.read(projectId, session);
    Project project = projects.findById(projectId).orElseThrow();
    List<Task> selectedTasks = scopedTasks(projectId, sprintId, filters, session);

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
    if (selectedTasks.isEmpty())
      lines.add(PdfReportWriter.Line.body("No tasks match this export."));
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
      Long projectId,
      Long sprintId,
      com.sprintflowinsight.reporting.TaskExportFilter filters,
      HttpSession session) {
    Instant now = Instant.now();
    var condition = filters.customCondition();
    java.util.Set<Long> matching =
        condition == null
            ? null
            : new java.util.HashSet<>(
                customFields.matching(projectId, condition, session).taskIds());
    return tasks.findByProjectId(projectId).stream()
        .filter(task -> matching == null || matching.contains(task.getId()))
        .filter(task -> sprintId == null || sprintId.equals(task.getSprintId()))
        .filter(task -> filters.matches(task, now))
        .toList();
  }

  private String q(String value) {
    return com.sprintflowinsight.CsvCell.quote(value);
  }
}

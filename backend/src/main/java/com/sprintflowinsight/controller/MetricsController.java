package com.sprintflowinsight.controller;

import com.sprintflowinsight.model.ActivityEvent;
import com.sprintflowinsight.model.Task;
import com.sprintflowinsight.project.Project;
import com.sprintflowinsight.project.ProjectAccessService;
import com.sprintflowinsight.project.ProjectRepository;
import com.sprintflowinsight.repository.TaskRepository;
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

  public MetricsController(
      ProjectRepository projects,
      MetricsService metrics,
      ActivityService activity,
      TaskRepository tasks,
      ProjectAccessService access) {
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

    List<String> lines = new ArrayList<>();
    lines.add("SPRINTFLOW INSIGHT");
    lines.add("Project: " + project.getName());
    lines.add("Generated: " + EXPORT_TIME.format(Instant.now()));
    lines.add("Scope: " + (sprintId == null ? "All sprints" : "Sprint " + sprintId));
    lines.add("Filters: " + filters.description());
    lines.add("");
    lines.add("DELIVERY SUMMARY");
    long completed = selectedTasks.stream().filter(task -> "Done".equals(task.getStatus())).count();
    long completedPoints =
        selectedTasks.stream()
            .filter(task -> "Done".equals(task.getStatus()))
            .mapToLong(Task::getStoryPoints)
            .sum();
    lines.add("Selected tasks: " + selectedTasks.size() + "   |   Completed tasks: " + completed);
    lines.add(
        "Completed points: "
            + completedPoints
            + "   |   Open work: "
            + (selectedTasks.size() - completed));
    lines.add("Blocked tasks: " + selectedTasks.stream().filter(Task::isBlocked).count());
    lines.add("");
    lines.add("TASKS");
    if (selectedTasks.isEmpty()) {
      lines.add("No tasks match this export.");
    } else {
      for (Task task : selectedTasks) {
        lines.add(
            task.getTitle()
                + " — "
                + task.getStatus()
                + " — "
                + task.getStoryPoints()
                + " pts"
                + (task.getDueAt() == null ? "" : " — due " + EXPORT_TIME.format(task.getDueAt()))
                + (task.isBlocked() ? " — BLOCKED" : ""));
      }
    }
    response.setContentType("application/pdf");
    response.setHeader(
        "Content-Disposition", "attachment; filename=sprintflow-" + projectId + ".pdf");
    response.getOutputStream().write(buildPdf(lines));
  }

  private List<Task> scopedTasks(
      Long projectId, Long sprintId, com.sprintflowinsight.service.TaskExportFilter filters) {
    Instant now = Instant.now();
    return tasks.findByProjectId(projectId).stream()
        .filter(task -> sprintId == null || sprintId.equals(task.getSprintId()))
        .filter(task -> filters.matches(task, now))
        .toList();
  }

  private byte[] buildPdf(List<String> lines) throws IOException {
    List<String> wrapped = new ArrayList<>();
    for (String line : lines) {
      String remaining = line == null ? "" : line;
      while (remaining.length() > 88) {
        int cut = remaining.lastIndexOf(' ', 88);
        if (cut < 1) cut = 88;
        wrapped.add(remaining.substring(0, cut));
        remaining = remaining.substring(cut).stripLeading();
      }
      wrapped.add(remaining);
    }
    List<List<String>> pages = new ArrayList<>();
    for (int index = 0; index < wrapped.size(); index += 42)
      pages.add(wrapped.subList(index, Math.min(index + 42, wrapped.size())));
    if (pages.isEmpty()) pages.add(List.of("SprintFlow Insight"));

    int pageCount = pages.size();
    int fontId = 3 + pageCount * 2;
    List<byte[]> objects = new ArrayList<>();
    objects.add(ascii("<< /Type /Catalog /Pages 2 0 R >>"));
    StringBuilder kids = new StringBuilder("<< /Type /Pages /Kids [");
    for (int page = 0; page < pageCount; page++) kids.append(3 + page * 2).append(" 0 R ");
    kids.append("] /Count ").append(pageCount).append(" >>");
    objects.add(ascii(kids.toString()));

    for (int page = 0; page < pageCount; page++) {
      int pageId = 3 + page * 2;
      int contentId = pageId + 1;
      StringBuilder content = new StringBuilder("BT\n/F1 11 Tf\n48 750 Td\n15 TL\n");
      for (String line : pages.get(page))
        content.append('(').append(pdfEscape(line)).append(") Tj\nT*\n");
      content.append("ET\n");
      byte[] stream = content.toString().getBytes(StandardCharsets.ISO_8859_1);
      objects.add(
          ascii(
              "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 "
                  + fontId
                  + " 0 R >> >> /Contents "
                  + contentId
                  + " 0 R >>"));
      ByteArrayOutputStream contentObject = new ByteArrayOutputStream();
      contentObject.write(ascii("<< /Length " + stream.length + " >>\nstream\n"));
      contentObject.write(stream);
      contentObject.write(ascii("endstream"));
      objects.add(contentObject.toByteArray());
    }
    objects.add(
        ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"));

    ByteArrayOutputStream pdf = new ByteArrayOutputStream();
    pdf.write(ascii("%PDF-1.4\n%âãÏÓ\n"));
    List<Integer> offsets = new ArrayList<>();
    offsets.add(0);
    for (int index = 0; index < objects.size(); index++) {
      offsets.add(pdf.size());
      pdf.write(ascii((index + 1) + " 0 obj\n"));
      pdf.write(objects.get(index));
      pdf.write(ascii("\nendobj\n"));
    }
    int xref = pdf.size();
    pdf.write(ascii("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n"));
    for (int offset = 1; offset < offsets.size(); offset++)
      pdf.write(ascii(String.format(Locale.ROOT, "%010d 00000 n \n", offsets.get(offset))));
    pdf.write(
        ascii(
            "trailer\n<< /Size "
                + (objects.size() + 1)
                + " /Root 1 0 R >>\nstartxref\n"
                + xref
                + "\n%%EOF"));
    return pdf.toByteArray();
  }

  private byte[] ascii(String value) {
    return value.getBytes(StandardCharsets.ISO_8859_1);
  }

  private String pdfEscape(String value) {
    return value
        .replaceAll("[^\\x20-\\xFF]", "?")
        .replace("\\", "\\\\")
        .replace("(", "\\(")
        .replace(")", "\\)");
  }

  private String q(String value) {
    return com.sprintflowinsight.CsvCell.quote(value);
  }
}

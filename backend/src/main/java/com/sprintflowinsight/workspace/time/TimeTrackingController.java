package com.sprintflowinsight.workspace.time;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Time entries, the timer, task estimates, sprint capacity and the time report (B4). */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class TimeTrackingController {
  private final TimeTrackingService time;
  private final CapacityService capacity;

  public TimeTrackingController(TimeTrackingService time, CapacityService capacity) {
    this.time = time;
    this.capacity = capacity;
  }

  @GetMapping("/time-entries")
  public TimeEntryPage list(
      @PathVariable Long projectId,
      @RequestParam(required = false) Long taskId,
      @RequestParam(required = false) Long sprintId,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) Long before,
      @RequestParam(required = false) Integer size,
      HttpSession session) {
    return time.list(projectId, taskId, sprintId, userId, before, size, session);
  }

  @PostMapping("/time-entries")
  public ResponseEntity<TimeEntryResponse> log(
      @PathVariable Long projectId,
      @Valid @RequestBody TimeEntryRequest request,
      HttpSession session) {
    return ResponseEntity.status(201).body(time.log(projectId, request, session));
  }

  @PutMapping("/time-entries/{entryId}")
  public TimeEntryResponse update(
      @PathVariable Long projectId,
      @PathVariable Long entryId,
      @Valid @RequestBody TimeEntryUpdateRequest request,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    return time.update(projectId, entryId, request, ifMatch, session);
  }

  @DeleteMapping("/time-entries/{entryId}")
  public ResponseEntity<Void> delete(
      @PathVariable Long projectId,
      @PathVariable Long entryId,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    time.delete(projectId, entryId, ifMatch, session);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/timer")
  public ResponseEntity<TimeEntryResponse> timer(
      @PathVariable Long projectId, HttpSession session) {
    return time.runningTimer(projectId, session)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PostMapping("/timer")
  public ResponseEntity<TimeEntryResponse> startTimer(
      @PathVariable Long projectId,
      @Valid @RequestBody TimerStartRequest request,
      HttpSession session) {
    return ResponseEntity.status(201).body(time.startTimer(projectId, request, session));
  }

  @PostMapping("/timer/stop")
  public TimeEntryResponse stopTimer(
      @PathVariable Long projectId,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    return time.stopTimer(projectId, ifMatch, session);
  }

  @PutMapping("/tasks/{taskId}/time-estimate")
  public TaskEstimateResponse estimate(
      @PathVariable Long projectId,
      @PathVariable Long taskId,
      @Valid @RequestBody EstimateRequest request,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    return time.estimate(projectId, taskId, request, ifMatch, session);
  }

  @GetMapping("/sprints/{sprintId}/capacity")
  public SprintCapacityResponse capacity(
      @PathVariable Long projectId, @PathVariable Long sprintId, HttpSession session) {
    return capacity.view(projectId, sprintId, session);
  }

  @PutMapping("/sprints/{sprintId}/capacity/{userId}")
  public SprintCapacityResponse setCapacity(
      @PathVariable Long projectId,
      @PathVariable Long sprintId,
      @PathVariable Long userId,
      @Valid @RequestBody CapacityRequest request,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    return capacity.set(projectId, sprintId, userId, request, ifMatch, session);
  }

  @DeleteMapping("/sprints/{sprintId}/capacity/{userId}")
  public ResponseEntity<Void> clearCapacity(
      @PathVariable Long projectId,
      @PathVariable Long sprintId,
      @PathVariable Long userId,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    capacity.clear(projectId, sprintId, userId, ifMatch, session);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/time-report")
  public TimeReport report(
      @PathVariable Long projectId,
      @RequestParam(required = false) Long sprintId,
      HttpSession session) {
    return capacity.report(projectId, sprintId, session);
  }

  @GetMapping(value = "/time-report.csv", produces = "text/csv")
  public ResponseEntity<byte[]> reportCsv(
      @PathVariable Long projectId,
      @RequestParam(required = false) Long sprintId,
      HttpSession session) {
    String csv = capacity.report(projectId, sprintId, session).toCsv();
    String name =
        "sprintflow-time-" + projectId + (sprintId == null ? "" : "-sprint-" + sprintId) + ".csv";
    return ResponseEntity.ok()
        .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(name).build().toString())
        .body(csv.getBytes(StandardCharsets.UTF_8));
  }
}

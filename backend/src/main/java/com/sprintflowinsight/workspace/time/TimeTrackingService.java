package com.sprintflowinsight.workspace.time;

import com.sprintflowinsight.VersionPrecondition;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.common.error.ErrorCode;
import com.sprintflowinsight.workspace.activity.ActivityService;
import com.sprintflowinsight.workspace.project.AccessContext;
import com.sprintflowinsight.workspace.project.AccessContext.Need;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectMemberRepository;
import com.sprintflowinsight.workspace.project.ProjectMutationService;
import com.sprintflowinsight.workspace.project.ProjectRole;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskRepository;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Time entries, the timer and task estimates. A person's entries may be changed by that person or
 * by an owner or admin; viewers and archived projects never accept changes.
 */
@Service
public class TimeTrackingService {
  static final int DEFAULT_PAGE_SIZE = 50;
  static final int MAX_PAGE_SIZE = 200;

  /** Oldest day that can be logged; protects reports from typos such as year 0202. */
  static final LocalDate EARLIEST_DAY = LocalDate.of(2000, 1, 1);

  private final TimeEntryRepository entries;
  private final TaskRepository tasks;
  private final ProjectMemberRepository members;
  private final ProjectAccessService access;
  private final Clock clock;
  private final com.sprintflowinsight.workspace.project.ProjectRepository projects;
  private final ProjectMutationService mutations;
  private final ActivityService activity;

  public TimeTrackingService(
      TimeEntryRepository entries,
      TaskRepository tasks,
      ProjectMemberRepository members,
      ProjectAccessService access,
      ProjectMutationService mutations,
      ActivityService activity,
      com.sprintflowinsight.workspace.project.ProjectRepository projects) {
    this.projects = projects;
    this.mutations = mutations;
    this.activity = activity;
    this.entries = entries;
    this.tasks = tasks;
    this.members = members;
    this.access = access;
    this.clock = Clock.systemUTC();
  }

  @Transactional(readOnly = true)
  public TimeEntryPage list(
      Long projectId,
      Long taskId,
      Long sprintId,
      Long userId,
      Long before,
      Integer size,
      HttpSession session) {
    access.read(projectId, session);
    int limit = size == null ? DEFAULT_PAGE_SIZE : size;
    if (limit < 1 || limit > MAX_PAGE_SIZE)
      throw new ApiException(
          ErrorCode.INVALID_PARAMETER, "Page size must be between 1 and " + MAX_PAGE_SIZE + ".");
    List<TimeEntryRow> rows =
        entries.page(projectId, taskId, sprintId, userId, before, Limit.of(limit + 1));
    boolean more = rows.size() > limit;
    List<TimeEntryRow> page = more ? rows.subList(0, limit) : rows;
    Long next = more ? page.get(limit - 1).entry().getId() : null;
    return new TimeEntryPage(page.stream().map(TimeEntryResponse::of).toList(), next);
  }

  @Transactional
  public TimeEntryResponse log(Long projectId, TimeEntryRequest request, HttpSession session) {
    AccessContext context = access.require(projectId, session, Need.WRITE);
    mutations.lock(projectId);
    Long person = request.userId() == null ? context.userId() : request.userId();
    if (!person.equals(context.userId())) {
      access.require(projectId, session, Need.MANAGE);
      requireWorker(projectId, person);
    }
    liveTask(projectId, request.taskId());
    requirePlausibleDay(request.spentOn());
    TimeEntry entry = new TimeEntry();
    entry.setProjectId(projectId);
    entry.setTaskId(request.taskId());
    entry.setUserId(person);
    entry.setMinutes(request.minutes());
    entry.setSpentOn(request.spentOn());
    entry.setNote(note(request.note()));
    entry.setCreatedAt(clock.instant());
    TimeEntry saved = entries.saveAndFlush(entry);
    activity.record(projectId, "TIME_CHANGED", "Updated project time log.", access.email(session));
    return respond(saved);
  }

  @Transactional
  public TimeEntryResponse update(
      Long projectId,
      Long entryId,
      TimeEntryUpdateRequest request,
      String ifMatch,
      HttpSession session) {
    TimeEntry entry = editable(projectId, entryId, session);
    VersionPrecondition.require(ifMatch, entry.getVersion());
    if (entry.running())
      throw ApiException.conflict("Stop the timer before correcting this entry.");
    requirePlausibleDay(request.spentOn());
    entry.setMinutes(request.minutes());
    entry.setSpentOn(request.spentOn());
    entry.setNote(note(request.note()));
    TimeEntry saved = entries.saveAndFlush(entry);
    activity.record(projectId, "TIME_CHANGED", "Updated project time log.", access.email(session));
    return respond(saved);
  }

  @Transactional
  public void delete(Long projectId, Long entryId, String ifMatch, HttpSession session) {
    TimeEntry entry = editable(projectId, entryId, session);
    VersionPrecondition.require(ifMatch, entry.getVersion());
    entries.delete(entry);
    activity.record(projectId, "TIME_CHANGED", "Updated project time log.", access.email(session));
  }

  /** The caller's running timer, if it runs in this project. */
  @Transactional(readOnly = true)
  public Optional<TimeEntryResponse> runningTimer(Long projectId, HttpSession session) {
    AccessContext context = access.require(projectId, session, Need.READ);
    return entries
        .findRunning(context.userId())
        .filter(entry -> projectId.equals(entry.getProjectId()))
        .map(this::respond);
  }

  @Transactional
  public TimeEntryResponse startTimer(
      Long projectId, TimerStartRequest request, HttpSession session) {
    AccessContext context = access.require(projectId, session, Need.WRITE);
    mutations.lock(projectId);
    liveTask(projectId, request.taskId());
    entries
        .findRunning(context.userId())
        .ifPresent(
            running -> {
              boolean writable =
                  projects
                      .findAccessRow(running.getProjectId(), context.userId())
                      .map(
                          row ->
                              row.archivedAt() == null
                                  && row.trashedAt() == null
                                  && row.memberRole() != null
                                  && ProjectRole.fromLabel(row.memberRole()).canWrite())
                      .orElse(false);
              if (!writable) {
                entries.delete(running);
                entries.flush();
                return;
              }
              throw ApiException.conflict(
                  projectId.equals(running.getProjectId())
                      ? "A timer is already running. Stop it before starting another."
                      : "A timer is already running in another project. Stop it first.");
            });
    Instant now = clock.instant();
    TimeEntry entry = new TimeEntry();
    entry.setProjectId(projectId);
    entry.setTaskId(request.taskId());
    entry.setUserId(context.userId());
    entry.setStartedAt(now);
    entry.setSpentOn(LocalDate.ofInstant(now, ZoneOffset.UTC));
    entry.setNote(note(request.note()));
    entry.setCreatedAt(now);
    // A concurrent start loses on the time_entry_one_running_timer index and gets 409.
    TimeEntry saved = entries.saveAndFlush(entry);
    activity.record(projectId, "TIME_CHANGED", "Updated project time log.", access.email(session));
    return respond(saved);
  }

  @Transactional
  public TimeEntryResponse stopTimer(Long projectId, String ifMatch, HttpSession session) {
    AccessContext context = access.require(projectId, session, Need.WRITE);
    mutations.lock(projectId);
    TimeEntry entry =
        entries
            .findRunning(context.userId())
            .filter(running -> projectId.equals(running.getProjectId()))
            .orElseThrow(() -> ApiException.notFound("No timer is running in this project."));
    VersionPrecondition.require(ifMatch, entry.getVersion());
    entry.setMinutes(CapacityMath.timerMinutes(entry.getStartedAt(), clock.instant()));
    TimeEntry saved = entries.saveAndFlush(entry);
    activity.record(projectId, "TIME_CHANGED", "Updated project time log.", access.email(session));
    return respond(saved);
  }

  @Transactional
  public TaskEstimateResponse estimate(
      Long projectId, Long taskId, EstimateRequest request, String ifMatch, HttpSession session) {
    access.require(projectId, session, Need.WRITE);
    mutations.lock(projectId);
    Task task = liveTask(projectId, taskId);
    VersionPrecondition.require(ifMatch, task.getVersion());
    task.setEstimateMinutes(request.estimateMinutes());
    Task saved = tasks.saveAndFlush(task);
    activity.record(
        projectId, "TIME_ESTIMATE_CHANGED", "Updated task time estimate.", access.email(session));
    return new TaskEstimateResponse(saved.getId(), saved.getEstimateMinutes(), saved.getVersion());
  }

  private TimeEntry editable(Long projectId, Long entryId, HttpSession session) {
    AccessContext context = access.require(projectId, session, Need.WRITE);
    mutations.lock(projectId);
    TimeEntry entry =
        entries
            .findById(entryId)
            .filter(found -> projectId.equals(found.getProjectId()))
            .orElseThrow(() -> ApiException.notFound("Time entry not found."));
    if (!context.userId().equals(entry.getUserId()))
      access.require(projectId, session, Need.MANAGE);
    return entry;
  }

  private TimeEntryResponse respond(TimeEntry entry) {
    return entries
        .row(entry.getId())
        .map(TimeEntryResponse::of)
        .orElseThrow(() -> ApiException.notFound("Time entry not found."));
  }

  private Task liveTask(Long projectId, Long taskId) {
    return tasks
        .findById(taskId)
        .filter(task -> projectId.equals(task.getProjectId()) && task.getTrashedAt() == null)
        .orElseThrow(() -> ApiException.badRequest("Choose a task in this project."));
  }

  /** Time can be logged for owners, admins and members, never for viewers or outsiders. */
  void requireWorker(Long projectId, Long userId) {
    boolean worker =
        members
            .findByProjectIdAndUserId(projectId, userId)
            .map(member -> ProjectRole.fromLabel(member.getRole()).canWrite())
            .orElse(false);
    if (!worker) throw ApiException.badRequest("Choose a member who works in this project.");
  }

  private void requirePlausibleDay(LocalDate day) {
    // One day of slack: the client's local date can be ahead of UTC.
    LocalDate latest = LocalDate.now(clock).plusDays(1);
    if (day.isBefore(EARLIEST_DAY) || day.isAfter(latest))
      throw ApiException.badRequest("Time can only be logged for past days or today.");
  }

  private static String note(String note) {
    return note == null || note.isBlank() ? null : note.trim();
  }
}

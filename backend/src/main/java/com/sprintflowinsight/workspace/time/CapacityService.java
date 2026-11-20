package com.sprintflowinsight.workspace.time;

import com.sprintflowinsight.VersionPrecondition;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.common.error.ErrorCode;
import com.sprintflowinsight.workspace.activity.ActivityService;
import com.sprintflowinsight.workspace.project.AccessContext;
import com.sprintflowinsight.workspace.project.AccessContext.Need;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectMember;
import com.sprintflowinsight.workspace.project.ProjectMemberRepository;
import com.sprintflowinsight.workspace.project.ProjectMutationService;
import com.sprintflowinsight.workspace.project.ProjectRole;
import com.sprintflowinsight.workspace.sprint.Sprint;
import com.sprintflowinsight.workspace.sprint.SprintRepository;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskRepository;
import com.sprintflowinsight.workspace.time.CapacityBoard.Committed;
import com.sprintflowinsight.workspace.time.CapacityBoard.MemberInput;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Member capacity per sprint, the capacity view against committed work, and the estimated against
 * actual report. Every read costs a fixed number of queries, independent of team and sprint size.
 */
@Service
public class CapacityService {
  private final SprintCapacityRepository capacities;
  private final TimeEntryRepository entries;
  private final SprintRepository sprints;
  private final TaskRepository tasks;
  private final ProjectMemberRepository members;
  private final ProjectAccessService access;
  private final ProjectMutationService mutations;
  private final ActivityService activity;

  public CapacityService(
      SprintCapacityRepository capacities,
      TimeEntryRepository entries,
      SprintRepository sprints,
      TaskRepository tasks,
      ProjectMemberRepository members,
      ProjectAccessService access,
      ProjectMutationService mutations,
      ActivityService activity) {
    this.mutations = mutations;
    this.activity = activity;
    this.capacities = capacities;
    this.entries = entries;
    this.sprints = sprints;
    this.tasks = tasks;
    this.members = members;
    this.access = access;
  }

  @Transactional(readOnly = true)
  public SprintCapacityResponse view(Long projectId, Long sprintId, HttpSession session) {
    access.read(projectId, session);
    return board(projectId, liveSprint(projectId, sprintId));
  }

  /**
   * Capacity in the optimizer's input shape (C1). Performs no access check: the caller must already
   * have verified read access to the project.
   */
  @Transactional(readOnly = true)
  public OptimizerCapacity optimizerCapacity(Long projectId, Long sprintId, CapacityUnit unit) {
    return OptimizerCapacity.from(board(projectId, liveSprint(projectId, sprintId)), unit);
  }

  @Transactional
  public SprintCapacityResponse set(
      Long projectId,
      Long sprintId,
      Long userId,
      CapacityRequest request,
      String ifMatch,
      HttpSession session) {
    Sprint sprint = editableSprint(projectId, sprintId, userId, session);
    SprintCapacity capacity =
        capacities
            .findBySprintIdAndUserId(sprintId, userId)
            .map(
                existing -> {
                  VersionPrecondition.require(ifMatch, existing.getVersion());
                  return existing;
                })
            .orElseGet(
                () -> {
                  // A version for a row that does not exist means someone else removed it.
                  if (ifMatch != null && !ifMatch.isBlank())
                    throw new ApiException(
                        ErrorCode.PRECONDITION_FAILED,
                        "This capacity was removed by someone else. Reload and try again.");
                  SprintCapacity created = new SprintCapacity();
                  created.setProjectId(projectId);
                  created.setSprintId(sprintId);
                  created.setUserId(userId);
                  return created;
                });
    capacity.setMinutesPerDay(request.minutesPerDay());
    capacity.setAvailabilityPercent(request.availabilityPercent());
    capacity.setPointCapacity(request.pointCapacity());
    capacity.setDaysOff(request.daysOff() == null ? List.of() : request.daysOff());
    capacity.setUpdatedAt(Instant.now());
    capacities.saveAndFlush(capacity);
    activity.record(
        projectId, "CAPACITY_CHANGED", "Updated sprint capacity.", access.email(session));
    return board(projectId, sprint);
  }

  @Transactional
  public void clear(
      Long projectId, Long sprintId, Long userId, String ifMatch, HttpSession session) {
    editableSprint(projectId, sprintId, userId, session);
    SprintCapacity capacity =
        capacities
            .findBySprintIdAndUserId(sprintId, userId)
            .orElseThrow(() -> ApiException.notFound("No capacity is set for this member."));
    VersionPrecondition.require(ifMatch, capacity.getVersion());
    capacities.delete(capacity);
    activity.record(
        projectId, "CAPACITY_CHANGED", "Updated sprint capacity.", access.email(session));
  }

  @Transactional(readOnly = true)
  public TimeReport report(Long projectId, Long sprintId, HttpSession session) {
    access.read(projectId, session);
    List<Task> scope;
    if (sprintId == null) {
      scope = tasks.findByProjectId(projectId);
    } else {
      liveSprint(projectId, sprintId);
      scope =
          tasks.findBySprintId(sprintId).stream()
              .filter(task -> projectId.equals(task.getProjectId()))
              .toList();
    }
    Map<Long, Long> logged = sums(entries.minutesByTask(projectId, sprintId));
    List<TimeReport.Line> lines =
        scope.stream()
            .sorted(
                Comparator.comparing(
                    Task::getNumber, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(
                task ->
                    new TimeReport.Line(
                        task.getId(),
                        task.getKey(),
                        task.getTitle(),
                        task.getStatus(),
                        task.getAssigneeEmail(),
                        task.getEstimateMinutes(),
                        logged.getOrDefault(task.getId(), 0L)))
            .toList();
    return TimeReport.of(sprintId, lines);
  }

  private SprintCapacityResponse board(Long projectId, Sprint sprint) {
    Map<Long, SprintCapacity> settings =
        capacities.findBySprintId(sprint.getId()).stream()
            .collect(Collectors.toMap(SprintCapacity::getUserId, Function.identity()));
    List<MemberInput> workers =
        members.findByProjectId(projectId).stream()
            .filter(member -> ProjectRole.fromLabel(member.getRole()).canWrite())
            .sorted(
                Comparator.comparing(
                        (ProjectMember member) -> ProjectRole.fromLabel(member.getRole()))
                    .thenComparing(member -> CapacityBoard.normalise(member.getEmail())))
            .map(
                member ->
                    new MemberInput(
                        member.getUserId(),
                        member.getEmail(),
                        ProjectRole.fromLabel(member.getRole()).label(),
                        settings.get(member.getUserId())))
            .toList();
    // HashMap: the unassigned group has a null key.
    Map<String, Committed> committed = new HashMap<>();
    for (Object[] row : capacities.committedByAssignee(projectId, sprint.getId())) {
      committed.put((String) row[0], new Committed(number(row[1]), number(row[2]), number(row[3])));
    }
    Map<Long, Long> logged = sums(entries.minutesByMemberInSprint(projectId, sprint.getId()));
    return CapacityBoard.build(
        new CapacityBoard.SprintInput(
            sprint.getId(), sprint.getName(), sprint.getStartDate(), sprint.getEndDate()),
        workers,
        committed,
        logged);
  }

  private Sprint editableSprint(Long projectId, Long sprintId, Long userId, HttpSession session) {
    AccessContext context = access.require(projectId, session, Need.WRITE);
    mutations.lock(projectId);
    if (!userId.equals(context.userId())) access.require(projectId, session, Need.MANAGE);
    Sprint sprint = liveSprint(projectId, sprintId);
    boolean worker =
        members
            .findByProjectIdAndUserId(projectId, userId)
            .map(member -> ProjectRole.fromLabel(member.getRole()).canWrite())
            .orElse(false);
    if (!worker) throw ApiException.badRequest("Choose a member who works in this project.");
    return sprint;
  }

  private Sprint liveSprint(Long projectId, Long sprintId) {
    return sprints
        .findById(sprintId)
        .filter(sprint -> projectId.equals(sprint.getProjectId()) && sprint.getTrashedAt() == null)
        .orElseThrow(() -> ApiException.notFound("Sprint not found."));
  }

  private static Map<Long, Long> sums(List<Object[]> rows) {
    Map<Long, Long> sums = new HashMap<>();
    for (Object[] row : rows)
      sums.put(row[0] == null ? null : ((Number) row[0]).longValue(), number(row[1]));
    return sums;
  }

  private static long number(Object value) {
    return value == null ? 0 : ((Number) value).longValue();
  }
}

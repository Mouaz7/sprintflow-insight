package com.sprintflowinsight.automation;

import com.sprintflowinsight.workspace.collaboration.CollaborationService;
import com.sprintflowinsight.workspace.customfield.CustomFieldService;
import com.sprintflowinsight.workspace.events.NotificationRequested;
import com.sprintflowinsight.workspace.project.ProjectMutationService;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AutomationWorker {
  static final int MAX_DEPTH = 3;

  private final AutomationRules rules;
  private final ProjectMutationService mutations;
  private final CustomFieldService fields;
  private final TaskService tasks;
  private final CollaborationService collaboration;
  private final ApplicationEventPublisher events;
  private final JdbcTemplate jdbc;

  AutomationWorker(
      AutomationRules rules,
      ProjectMutationService mutations,
      CustomFieldService fields,
      TaskService tasks,
      CollaborationService collaboration,
      ApplicationEventPublisher events,
      JdbcTemplate jdbc) {
    this.rules = rules;
    this.mutations = mutations;
    this.fields = fields;
    this.tasks = tasks;
    this.collaboration = collaboration;
    this.events = events;
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void run(AutomationSignal signal, long ruleId) {
    // Archive and trash checks share the ordinary write lock, including changes racing this event.
    var project =
        jdbc.queryForList(
            "select id from projects where id=? and trashed_at is null and archived_at is null",
            Long.class,
            signal.projectId());
    if (project.isEmpty()) return;
    mutations.lock(signal.projectId());
    var current =
        rules
            .find(signal.projectId(), ruleId)
            .filter(rule -> rule.enabled() && rule.trigger() == signal.kind());
    if (current.isEmpty() || alreadyRan(signal, ruleId)) return;
    var rule = current.orElseThrow();
    var compiled = AutomationDsl.parse(rule.source());
    if (!matchesTrigger(signal, compiled.trigger())) return;
    var task =
        tasks
            .gettaskById(signal.taskId())
            .filter(value -> value.getProjectId() == signal.projectId());
    if (task.isEmpty()) {
      record(signal, rule, "SKIPPED", "Task is unavailable.");
      return;
    }
    Task target = task.orElseThrow();
    if (signal.kind() == AutomationRule.TriggerKind.OVERDUE
        && (!Objects.equals(target.getDueAt(), signal.dueAt())
            || "Done".equals(target.getStatus()))) {
      record(signal, rule, "SKIPPED", "The task is no longer overdue.");
      return;
    }
    var trail = nextTrail(signal.trail(), ruleId);
    if (trail.isEmpty()) {
      record(signal, rule, "SKIPPED", "Stopped by the automation chain limit.");
      return;
    }
    if (!compiled.conditions().stream().allMatch(condition -> matches(target, condition))) {
      record(signal, rule, "SKIPPED", "The rule conditions did not match.");
      return;
    }
    String skipped = apply(target, compiled.action(), trail);
    record(
        signal,
        rule,
        skipped == null ? "APPLIED" : "SKIPPED",
        skipped == null ? "Action applied." : skipped);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void failed(AutomationSignal signal, AutomationRules.Rule rule) {
    if (!jdbc.queryForList("select id from projects where id=?", Long.class, signal.projectId())
        .isEmpty()) record(signal, rule, "FAILED", "The automation action could not be completed.");
  }

  private boolean alreadyRan(AutomationSignal signal, long ruleId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from automation_run where rule_id=? and task_id=? and"
                + " event_id=?)",
            Boolean.class,
            ruleId,
            signal.taskId(),
            signal.eventId()));
  }

  private void record(
      AutomationSignal signal, AutomationRules.Rule rule, String outcome, String detail) {
    jdbc.update(
        """
insert into automation_run(project_id,rule_id,rule_name,task_id,event_id,trigger_kind,outcome,detail)
values(?,?,?,?,?,?,?,?) on conflict(rule_id,task_id,event_id) do nothing
""",
        signal.projectId(),
        rule.id(),
        rule.name(),
        signal.taskId(),
        signal.eventId(),
        signal.kind().name(),
        outcome,
        detail);
  }

  private boolean matches(Task task, AutomationRule.Condition condition) {
    return switch (condition) {
      case AutomationRule.StatusIs status -> status.status().equals(task.getStatus());
      case AutomationRule.HasLabel label -> task.getLabels().contains(label.label().strip());
      case AutomationRule.CustomField field ->
          fields.matchesProjectTask(
              task.getProjectId(), task.getId(), AutomationRules.customCondition(field));
    };
  }

  private String apply(Task task, AutomationRule.Action action, List<Long> trail) {
    String value = action.value().strip();
    switch (action.kind()) {
      case COMMENT -> collaboration.addAutomationComment(task, value);
      case NOTIFY ->
          events.publishEvent(
              new NotificationRequested(
                  UUID.randomUUID(), task.getProjectId(), null, Instant.now(), null, value, true));
      case ASSIGN, LABEL, MOVE -> {
        Task changes = copy(task);
        switch (action.kind()) {
          case ASSIGN -> {
            String email = rules.assignee(task.getProjectId(), value).orElse(null);
            if (email == null) return "Assignee is no longer a project member.";
            if (email.equalsIgnoreCase(task.getAssigneeEmail()))
              return "The task already has this assignee.";
            changes.setAssigneeEmail(email);
          }
          case LABEL -> {
            var labels = new ArrayList<>(task.getLabels());
            if (labels.contains(value)) return "The task already has this label.";
            if (labels.size() >= 12) return "The task already has 12 labels.";
            labels.add(value);
            changes.setLabels(labels);
          }
          case MOVE -> {
            String skipped = moveBlocked(task, value);
            if (skipped != null) return skipped;
            changes.setStatus(value);
          }
          default -> throw new IllegalStateException("Expected a task update action.");
        }
        tasks.updateTask(task, changes, "automation@sprintflow.invalid", trail);
      }
    }
    return null;
  }

  static boolean matchesTrigger(AutomationSignal signal, AutomationRule.Trigger trigger) {
    return signal.kind() == trigger.kind()
        && (trigger.from() == null || trigger.from().equals(signal.from()))
        && (trigger.to() == null || trigger.to().equals(signal.to()));
  }

  static List<Long> nextTrail(List<Long> trail, long ruleId) {
    if (trail.size() >= MAX_DEPTH || trail.contains(ruleId)) return List.of();
    var expanded = new ArrayList<>(trail);
    expanded.add(ruleId);
    return List.copyOf(expanded);
  }

  static String moveBlocked(Task task, String status) {
    if (status.equals(task.getStatus())) return "The task is already in this column.";
    if ("Done".equals(status)
        && task.getChecklist().stream().anyMatch(step -> step.required() && !step.completed()))
      return "Required checklist steps are incomplete.";
    return null;
  }

  static Task copy(Task task) {
    var result = new Task(task.getTitle(), task.getDescription(), task.getStatus());
    result.setProjectId(task.getProjectId());
    result.setSprintId(task.getSprintId());
    result.setStoryId(task.getStoryId());
    result.setAssigneeEmail(task.getAssigneeEmail());
    result.setStoryPoints(task.getStoryPoints());
    result.setDueAt(task.getDueAt());
    result.setLabels(task.getLabels());
    result.setChecklist(task.getChecklist());
    result.setBlocked(task.isBlocked());
    result.setBlockReason(task.getBlockReason());
    return result;
  }
}

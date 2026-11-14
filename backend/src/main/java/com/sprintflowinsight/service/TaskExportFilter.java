package com.sprintflowinsight.service;

import com.sprintflowinsight.model.Task;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Board filters shared by CSV and PDF exports. Authorization happens before filtering. */
public record TaskExportFilter(
    String status,
    String sprint,
    String assignee,
    String due,
    Boolean blocked,
    String label,
    String q) {
  public TaskExportFilter {
    status = clean(status);
    sprint = clean(sprint);
    assignee = clean(assignee);
    due = clean(due);
    label = clean(label);
    q = clean(q);
    if ((!status.isEmpty() && !Set.of("To Do", "In Progress", "Review", "Done").contains(status))
        || (!sprint.isEmpty() && !sprint.equals("backlog"))
        || (!due.isEmpty() && !Set.of("overdue", "has-date", "no-date").contains(due))
        || label.length() > 32
        || q.length() > 1000
        || assignee.length() > 320)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid export filters.");
  }

  private static String clean(String value) {
    return value == null ? "" : value;
  }

  public boolean matches(Task task, Instant now) {
    return (status.isEmpty() || status.equals(task.getStatus()))
        && (sprint.isEmpty() || task.getSprintId() == null)
        && (assignee.isEmpty()
            || (assignee.equals("unassigned")
                ? task.getAssigneeEmail() == null || task.getAssigneeEmail().isBlank()
                : assignee.equalsIgnoreCase(task.getAssigneeEmail())))
        && (due.isEmpty()
            || switch (due) {
              case "overdue" ->
                  task.getDueAt() != null
                      && task.getDueAt().isBefore(now)
                      && !"Done".equals(task.getStatus());
              case "has-date" -> task.getDueAt() != null;
              default -> task.getDueAt() == null;
            })
        && (!Boolean.TRUE.equals(blocked) || task.isBlocked())
        && (label.isEmpty() || task.getLabels().contains(label))
        && (task.getTitle() + " " + (task.getDescription() == null ? "" : task.getDescription()))
            .toLowerCase(Locale.ROOT)
            .contains(q.toLowerCase(Locale.ROOT));
  }

  public String description() {
    return "status="
        + status
        + "; sprint="
        + sprint
        + "; assignee="
        + assignee
        + "; due="
        + due
        + "; blocked="
        + Boolean.TRUE.equals(blocked)
        + "; label="
        + label
        + "; search="
        + q;
  }
}

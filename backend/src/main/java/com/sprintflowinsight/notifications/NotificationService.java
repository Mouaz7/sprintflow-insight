package com.sprintflowinsight.notifications;

import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.auth.UserPreferences;
import com.sprintflowinsight.auth.UserPreferencesRepository;
import com.sprintflowinsight.auth.UserRepository;
import com.sprintflowinsight.workspace.project.ProjectMemberRepository;
import com.sprintflowinsight.workspace.project.ProjectRepository;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {
  private final org.springframework.context.ApplicationEventPublisher events;
  private final UserPreferencesRepository preferences;
  private final UserRepository users;

  private final NotificationRepository repository;
  private final ProjectRepository projects;
  private final ProjectMemberRepository members;
  private final TaskRepository tasks;

  public NotificationService(
      NotificationRepository repository,
      ProjectRepository projects,
      ProjectMemberRepository members,
      TaskRepository tasks,
      UserPreferencesRepository preferences,
      UserRepository users,
      org.springframework.context.ApplicationEventPublisher events) {
    this.events = events;
    this.repository = repository;
    this.projects = projects;
    this.members = members;
    this.tasks = tasks;
    this.preferences = preferences;
    this.users = users;
  }

  /**
   * Notifies the project owner, resolved now so a changed owner email is honoured. Skips quietly
   * when the owner has no account, is the actor, or turned notifications off.
   */
  public void notifyProjectOwner(
      Long projectId, Long actorUserId, String message, java.util.UUID sourceEventId) {
    if (projectId == null) return;
    projects
        .findById(projectId)
        .map(project -> project.getOwnerEmail())
        .flatMap(email -> users.findByEmail(email.trim().toLowerCase(Locale.ROOT)))
        .filter(owner -> !owner.getId().equals(actorUserId))
        .ifPresent(owner -> deliver(projectId, owner, message, sourceEventId));
  }

  /**
   * Notifies a user by id, resolving the current email at delivery time. A user deleted since the
   * event was published is skipped quietly.
   */
  public void notifyRecipient(
      Long projectId,
      Long recipientUserId,
      Long actorUserId,
      String message,
      java.util.UUID sourceEventId) {
    if (projectId == null || recipientUserId == null || recipientUserId.equals(actorUserId)) return;
    users
        .findById(recipientUserId)
        .ifPresent(
            recipient -> {
              if (repository.existsByProjectIdAndRecipientEmailAndMessageAndCreatedAtAfter(
                  projectId,
                  normalize(recipient.getEmail()),
                  message,
                  Instant.now().minusSeconds(5))) return;
              deliver(projectId, recipient, message, sourceEventId);
            });
  }

  /** Mention delivery is idempotent by event, without suppressing a separate comment. */
  public void notifyMention(
      Long projectId, Long recipientId, Long actorId, String message, java.util.UUID source) {
    if (recipientId.equals(actorId)) return;
    boolean visible =
        projects
            .findAccessRow(projectId, recipientId)
            .map(row -> row.trashedAt() == null && row.memberRole() != null)
            .orElse(false);
    if (!visible) return;
    users.findById(recipientId).ifPresent(user -> deliver(projectId, user, message, source));
  }

  private void deliver(
      Long projectId, User recipient, String message, java.util.UUID sourceEventId) {
    String email = normalize(recipient.getEmail());
    if (!preferences.findById(recipient.getId()).map(UserPreferences::isNotifications).orElse(true))
      return;
    if (projects.findById(projectId).isEmpty()) return;
    if (sourceEventId != null
        && repository.existsBySourceEventIdAndRecipientEmail(sourceEventId, email)) return;
    saveNotification(projectId, email, message, sourceEventId);
  }

  private static String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private void saveNotification(Long projectId, String recipient, String message) {
    saveNotification(projectId, recipient, message, null);
  }

  private void saveNotification(
      Long projectId, String recipient, String message, java.util.UUID sourceEventId) {
    Notification notification = new Notification();
    notification.setSourceEventId(sourceEventId);
    notification.setProjectId(projectId);
    notification.setRecipientEmail(normalize(recipient));
    notification.setMessage(message);
    notification.setCreatedAt(Instant.now());
    notification.setRead(false);
    repository.save(notification);
    events.publishEvent(
        new NotificationDelivered(java.util.UUID.randomUUID(), projectId, Instant.now()));
  }

  /** Notifications addressed to the account's current email, for projects it is a member of. */
  public List<Notification> forRecipient(Long userId, String email, Pageable pageable) {
    return repository.findVisibleTo(userId, email.trim().toLowerCase(Locale.ROOT), pageable);
  }

  public void markRead(Long id, String email) {
    var n =
        repository
            .findById(id)
            .filter(item -> item.getRecipientEmail().equalsIgnoreCase(email))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    n.setRead(true);
    repository.save(n);
  }

  @Transactional
  public void markAllRead(String email) {
    repository.markAllRead(email);
  }

  private boolean enabled(String email) {
    return users
        .findByEmail(email.toLowerCase(Locale.ROOT))
        .flatMap(user -> preferences.findById(user.getId()))
        .map(UserPreferences::isNotifications)
        .orElse(true);
  }

  public void deleteForUser(String email) {
    repository.deleteByRecipientEmail(email);
  }

  @Transactional
  @Scheduled(cron = "0 0 9 * * *", zone = "Europe/Stockholm")
  public void sendDueDateReminders() {
    ZoneId zone = ZoneId.of("Europe/Stockholm");
    LocalDate today = LocalDate.now(zone);
    Instant todayStart = today.atStartOfDay(zone).toInstant();
    int page = 0;
    Map<String, Boolean> notificationPreferences = new HashMap<>();
    Slice<Task> dueTasks;
    do {
      dueTasks =
          tasks.findDueForReminder(
              today.plusDays(1).atStartOfDay(zone).toInstant(), PageRequest.of(page++, 200));
      var projectIds = dueTasks.stream().map(Task::getProjectId).collect(Collectors.toSet());
      Map<Long, Set<String>> recipientsByProject = new HashMap<>();
      projects
          .findAllById(projectIds)
          .forEach(
              project -> {
                if (project.getTrashedAt() == null && project.getArchivedAt() == null) {
                  Set<String> recipients = new LinkedHashSet<>();
                  recipients.add(project.getOwnerEmail().toLowerCase(Locale.ROOT));
                  recipientsByProject.put(project.getId(), recipients);
                }
              });
      if (!projectIds.isEmpty())
        members
            .findByProjectIdIn(projectIds)
            .forEach(
                member -> {
                  var recipients = recipientsByProject.get(member.getProjectId());
                  if (recipients != null)
                    recipients.add(member.getEmail().toLowerCase(Locale.ROOT));
                });
      for (Task task : dueTasks) {
        if (task.getDueAt() == null || "Done".equals(task.getStatus())) continue;
        LocalDate dueDate = task.getDueAt().atZone(zone).toLocalDate();
        if (dueDate.isAfter(today)) continue;
        String when = dueDate.isBefore(today) ? "is overdue" : "is due today";
        String message = "Reminder: “" + task.getTitle() + "” " + when + ".";
        var recipients = recipientsByProject.get(task.getProjectId());
        if (recipients == null) continue;
        for (String recipient : recipients) {
          if (!notificationPreferences.computeIfAbsent(recipient, this::enabled)) continue;
          if (repository.existsByProjectIdAndRecipientEmailAndMessageAndCreatedAtAfter(
              task.getProjectId(), recipient, message, todayStart)) continue;
          saveNotification(task.getProjectId(), recipient, message);
        }
      }
    } while (dueTasks.hasNext());
  }
}

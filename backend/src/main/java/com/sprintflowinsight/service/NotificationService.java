package com.sprintflowinsight.service;

import com.sprintflowinsight.auth.UserPreferences;
import com.sprintflowinsight.auth.UserPreferencesRepository;
import com.sprintflowinsight.auth.UserRepository;
import com.sprintflowinsight.model.Notification;
import com.sprintflowinsight.model.Task;
import com.sprintflowinsight.project.ProjectMemberRepository;
import com.sprintflowinsight.project.ProjectRepository;
import com.sprintflowinsight.repository.NotificationRepository;
import com.sprintflowinsight.repository.TaskRepository;
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
      UserRepository users) {
    this.repository = repository;
    this.projects = projects;
    this.members = members;
    this.tasks = tasks;
    this.preferences = preferences;
    this.users = users;
  }

  public void notifyProjectOwner(Long projectId, String actorEmail, String message) {
    if (projectId == null) return;
    projects
        .findById(projectId)
        .ifPresent(
            project -> {
              String recipient = project.getOwnerEmail();
              if (recipient != null
                  && enabled(recipient)
                  && !recipient.equalsIgnoreCase(actorEmail == null ? "" : actorEmail)) {
                saveNotification(projectId, recipient, message);
              }
            });
  }

  public void notifyRecipient(Long projectId, String recipient, String actor, String message) {
    if (recipient == null
        || recipient.equalsIgnoreCase(actor == null ? "" : actor)
        || !enabled(recipient)) return;
    recipient = recipient.trim().toLowerCase(Locale.ROOT);
    if (repository.existsByProjectIdAndRecipientEmailAndMessageAndCreatedAtAfter(
        projectId, recipient, message, Instant.now().minusSeconds(5))) return;
    saveNotification(projectId, recipient, message);
  }

  private void saveNotification(Long projectId, String recipient, String message) {
    Notification notification = new Notification();
    notification.setProjectId(projectId);
    notification.setRecipientEmail(recipient.trim().toLowerCase(Locale.ROOT));
    notification.setMessage(message);
    notification.setCreatedAt(Instant.now());
    notification.setRead(false);
    repository.save(notification);
  }

  public List<Notification> forRecipient(String email) {
    return forRecipient(email, Pageable.unpaged());
  }

  public List<Notification> forRecipient(String email, Pageable pageable) {
    return repository.findVisibleTo(email.trim().toLowerCase(Locale.ROOT), pageable);
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

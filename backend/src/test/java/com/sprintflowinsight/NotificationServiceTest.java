package com.sprintflowinsight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.sprintflowinsight.auth.UserPreferencesRepository;
import com.sprintflowinsight.auth.UserRepository;
import com.sprintflowinsight.notifications.NotificationRepository;
import com.sprintflowinsight.notifications.NotificationService;
import com.sprintflowinsight.workspace.project.Project;
import com.sprintflowinsight.workspace.project.ProjectMemberRepository;
import com.sprintflowinsight.workspace.project.ProjectRepository;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

class NotificationServiceTest {
  @Test
  void sendsAllBatchesAndLoadsMembershipsOncePerBatch() {
    var notices = mock(NotificationRepository.class);
    var projects = mock(ProjectRepository.class);
    var members = mock(ProjectMemberRepository.class);
    var tasks = mock(TaskRepository.class);
    var users = mock(UserRepository.class);
    var preferences = mock(UserPreferencesRepository.class);
    var service = new NotificationService(notices, projects, members, tasks, preferences, users);
    var project = mock(Project.class);
    when(project.getId()).thenReturn(1L);
    when(project.getOwnerEmail()).thenReturn("owner@example.com");
    when(projects.findAllById(Set.of(1L))).thenReturn(List.of(project));
    when(members.findByProjectIdIn(Set.of(1L))).thenReturn(List.of());
    List<Task> due =
        IntStream.range(0, 201)
            .mapToObj(
                index -> {
                  Task task = new Task();
                  task.setProjectId(1L);
                  task.setTitle("Due task " + index);
                  task.setStatus("To Do");
                  task.setDueAt(Instant.now().minusSeconds(86400));
                  return task;
                })
            .toList();
    when(tasks.findDueForReminder(any(), any(Pageable.class)))
        .thenReturn(
            new SliceImpl<>(
                due.subList(0, 200), org.springframework.data.domain.PageRequest.of(0, 200), true),
            new SliceImpl<>(
                due.subList(200, 201),
                org.springframework.data.domain.PageRequest.of(1, 200),
                false));

    service.sendDueDateReminders();

    var pages = ArgumentCaptor.forClass(Pageable.class);
    verify(tasks, times(2)).findDueForReminder(any(), pages.capture());
    assertThat(pages.getAllValues()).extracting(Pageable::getPageNumber).containsExactly(0, 1);
    verify(members, times(2)).findByProjectIdIn(eq(Set.of(1L)));
    verify(users, times(1)).findByEmail("owner@example.com");
    verify(notices, times(201)).save(any());
  }

  @Test
  void deliversToTheRecipientsCurrentEmailAndSkipsDeletedUsersQuietly() {
    var notices = mock(NotificationRepository.class);
    var projects = mock(ProjectRepository.class);
    var users = mock(UserRepository.class);
    var preferences = mock(UserPreferencesRepository.class);
    var service =
        new NotificationService(
            notices,
            projects,
            mock(ProjectMemberRepository.class),
            mock(TaskRepository.class),
            preferences,
            users);
    var project = mock(Project.class);
    when(projects.findById(1L)).thenReturn(java.util.Optional.of(project));
    var recipient = new com.sprintflowinsight.auth.User();
    recipient.setId(7L);
    recipient.setEmail("Renamed@Example.com");
    when(users.findById(7L)).thenReturn(java.util.Optional.of(recipient));
    when(users.findById(8L)).thenReturn(java.util.Optional.empty());
    var event = java.util.UUID.randomUUID();

    service.notifyRecipient(1L, 7L, 3L, "Hello", event);
    service.notifyRecipient(1L, 8L, 3L, "Hello", java.util.UUID.randomUUID());
    service.notifyRecipient(1L, 7L, 7L, "Own change", java.util.UUID.randomUUID());

    var saved = ArgumentCaptor.forClass(com.sprintflowinsight.notifications.Notification.class);
    verify(notices, times(1)).save(saved.capture());
    assertThat(saved.getValue().getRecipientEmail()).isEqualTo("renamed@example.com");
    assertThat(saved.getValue().getSourceEventId()).isEqualTo(event);
  }

  @Test
  void projectOwnerIsResolvedAtDeliveryAndNotNotifiedOfTheirOwnAction() {
    var notices = mock(NotificationRepository.class);
    var projects = mock(ProjectRepository.class);
    var users = mock(UserRepository.class);
    var service =
        new NotificationService(
            notices,
            projects,
            mock(ProjectMemberRepository.class),
            mock(TaskRepository.class),
            mock(UserPreferencesRepository.class),
            users);
    var project = mock(Project.class);
    when(project.getOwnerEmail()).thenReturn("owner@example.com");
    when(projects.findById(1L)).thenReturn(java.util.Optional.of(project));
    var owner = new com.sprintflowinsight.auth.User();
    owner.setId(5L);
    owner.setEmail("owner@example.com");
    when(users.findByEmail("owner@example.com")).thenReturn(java.util.Optional.of(owner));

    service.notifyProjectOwner(1L, 5L, "Own move", java.util.UUID.randomUUID());
    verify(notices, never()).save(any());
    service.notifyProjectOwner(1L, null, "Legacy event", java.util.UUID.randomUUID());
    verify(notices, times(1)).save(any());
  }
}

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
}

package com.sprintflowinsight.workspace.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.common.error.ErrorCode;
import com.sprintflowinsight.workspace.lifecycle.LifecycleService;
import com.sprintflowinsight.workspace.project.Project;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectMemberRepository;
import com.sprintflowinsight.workspace.project.ProjectMutationService;
import com.sprintflowinsight.workspace.project.ProjectRepository;
import com.sprintflowinsight.workspace.sprint.SprintRepository;
import com.sprintflowinsight.workspace.story.UserStoryRepository;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TaskApplicationServiceTest {
  private final TaskService taskService = mock(TaskService.class);
  private final ProjectRepository projects = mock(ProjectRepository.class);
  private final ProjectAccessService access = mock(ProjectAccessService.class);
  private final HttpSession session = mock(HttpSession.class);
  private final TaskApplicationService service =
      new TaskApplicationService(
          taskService,
          mock(ProjectMutationService.class),
          projects,
          mock(ProjectMemberRepository.class),
          mock(SprintRepository.class),
          mock(UserStoryRepository.class),
          access,
          mock(LifecycleService.class));

  @ParameterizedTest
  @EnumSource(
      value = ErrorCode.class,
      names = {"NOT_FOUND", "FORBIDDEN", "BAD_REQUEST"})
  void lookupByKeyTurnsEveryAccessRefusalIntoTheSameNotFound(ErrorCode refusal) {
    Project project = new Project();
    project.setId(7L);
    project.setKey("SF");
    Task task = new Task();
    task.setId(70L);
    task.setProjectId(7L);
    when(projects.findByProjectKey("SF")).thenReturn(Optional.of(project));
    when(taskService.findByNumber(7L, 3)).thenReturn(Optional.of(task));
    doThrow(new ApiException(refusal, "Project is in the recycle bin."))
        .when(access)
        .read(eq(7L), any());

    assertThatThrownBy(() -> service.getTaskByKey("SF-3", session))
        .isInstanceOfSatisfying(
            ApiException.class,
            denied -> {
              assertThat(denied.code()).isEqualTo(ErrorCode.NOT_FOUND);
              assertThat(denied.getReason()).isEqualTo("Task not found.");
            });
  }
}

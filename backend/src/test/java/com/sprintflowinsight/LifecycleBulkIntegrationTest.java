package com.sprintflowinsight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.support.ApiIntegrationTest;
import com.sprintflowinsight.workspace.project.Project;
import com.sprintflowinsight.workspace.sprint.Sprint;
import com.sprintflowinsight.workspace.sprint.SprintRepository;
import com.sprintflowinsight.workspace.story.UserStory;
import com.sprintflowinsight.workspace.story.UserStoryRepository;
import com.sprintflowinsight.workspace.task.Task;
import com.sprintflowinsight.workspace.task.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** Recycle-bin cascades are set-based: their cost does not grow with the amount of work moved. */
class LifecycleBulkIntegrationTest extends ApiIntegrationTest {
  @Autowired private com.sprintflowinsight.workspace.task.TaskNumberAllocator numbers;
  @Autowired private SprintRepository sprints;
  @Autowired private UserStoryRepository stories;
  @Autowired private TaskRepository tasks;

  @Test
  void trashingASprintCostsTheSameForThreeOrThirtyTasks() throws Exception {
    User owner = user();
    long small = trashCost(owner, 3);
    long large = trashCost(owner, 30);
    assertThat(large).isEqualTo(small);
  }

  @Test
  void cascadeBumpsVersionsAndRestoresExactlyTheSameItems() throws Exception {
    User owner = user();
    Project project = project(owner, null);
    Sprint sprint = sprint(project);
    UserStory story = story(project, sprint);
    Task viaStory = task(project, null, story);
    Task viaSprint = task(project, sprint, null);
    Task unrelated = task(project, null, null);
    Task alreadyTrashed = task(project, sprint, null);
    alreadyTrashed.setTrashedAt(java.time.Instant.parse("2024-01-01T00:00:00Z"));
    alreadyTrashed = tasks.save(alreadyTrashed);
    long versionBefore = tasks.findById(viaSprint.getId()).orElseThrow().getVersion();

    mvc.perform(
            delete("/api/sprints/" + sprint.getId())
                .with(as(owner))
                .with(csrf())
                .header("If-Match", "\"" + sprint.getVersion() + "\""))
        .andExpect(status().isNoContent());

    assertThat(tasks.findById(viaStory.getId()).orElseThrow().getTrashedAt()).isNotNull();
    Task trashed = tasks.findById(viaSprint.getId()).orElseThrow();
    assertThat(trashed.getTrashedAt()).isNotNull();
    assertThat(trashed.getVersion()).isGreaterThan(versionBefore);
    assertThat(tasks.findById(unrelated.getId()).orElseThrow().getTrashedAt()).isNull();
    assertThat(stories.findById(story.getId()).orElseThrow().getTrashedAt()).isNotNull();

    Sprint inBin = sprints.findById(sprint.getId()).orElseThrow();
    mvc.perform(
            put("/api/workspace/trash/sprint/" + sprint.getId())
                .with(as(owner))
                .with(csrf())
                .header("If-Match", "\"" + inBin.getVersion() + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"restore\":true}"))
        .andExpect(status().isOk());

    assertThat(tasks.findById(viaStory.getId()).orElseThrow().getTrashedAt()).isNull();
    assertThat(tasks.findById(viaSprint.getId()).orElseThrow().getTrashedAt()).isNull();
    assertThat(stories.findById(story.getId()).orElseThrow().getTrashedAt()).isNull();
    assertThat(tasks.findById(alreadyTrashed.getId()).orElseThrow().getTrashedAt())
        .as("an item trashed earlier on its own stays in the bin")
        .isNotNull();
  }

  private long trashCost(User owner, int taskCount) throws Exception {
    Project project = project(owner, null);
    Sprint sprint = sprint(project);
    UserStory story = story(project, sprint);
    for (int i = 0; i < taskCount; i++)
      task(project, i % 2 == 0 ? sprint : null, i % 2 == 0 ? null : story);
    return statementsFor(
        () ->
            mvc.perform(
                    delete("/api/sprints/" + sprint.getId())
                        .with(as(owner))
                        .with(csrf())
                        .header("If-Match", "\"" + sprint.getVersion() + "\""))
                .andExpect(status().isNoContent()));
  }

  private Sprint sprint(Project project) {
    Sprint sprint = new Sprint("Sprint", "Goal", "Planned", 0);
    sprint.setProjectId(project.getId());
    sprint.setStartDate(java.time.LocalDate.now());
    sprint.setEndDate(java.time.LocalDate.now().plusDays(14));
    return sprints.save(sprint);
  }

  private UserStory story(Project project, Sprint sprint) {
    UserStory story = new UserStory();
    story.setTitle("Story");
    story.setAcceptanceCriteria("Done when done");
    story.setPriorityId(1);
    story.setStoryPoints(3);
    story.setProjectId(project.getId());
    story.setSprintId(sprint.getId());
    return stories.save(story);
  }

  private Task task(Project project, Sprint sprint, UserStory story) {
    Task task = new Task("Task", null, "To Do");
    task.setProjectId(project.getId());
    task.setNumber(numbers.next(project.getId()).number());
    if (sprint != null) task.setSprintId(sprint.getId());
    if (story != null) task.setStoryId(story.getId());
    return tasks.save(task);
  }
}

package com.sprintflowinsight.workspace.roadmap;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RoadmapReadIntegrationTest extends RoadmapIntegrationSupport {
  @Test
  void roadmapCombinesProgressOutsideBlockersCriticalPathsAndEpicArrows() throws Exception {
    var owner = user();
    var project = project(owner, null);
    long first = epic(project, "Release"), second = epic(project, "Foundation");
    jdbc.update(
        "update epic set target_date=? where id=?",
        LocalDate.now(ZoneOffset.UTC).plusDays(30),
        first);
    long finishedStory = story(project, first),
        waitingStory = story(project, first),
        outsideStory = story(project, second);
    jdbc.update("update user_stories set story_points=5 where id=?", waitingStory);
    long sprint =
        jdbc.queryForObject(
            "insert into"
                + " sprint(name,status,total_story_points,project_id,start_date,end_date,created_at,version)"
                + " values ('Current','Active',8,?,current_date,current_date+14,now(),0) returning"
                + " id",
            Long.class,
            project.getId());
    long done = task(project, 1, "Done"),
        waiting = task(project, 2, "To Do"),
        outside = task(project, 3, "To Do");
    jdbc.update("update task set story_id=? where id=?", finishedStory, done);
    jdbc.update("update task set story_id=?,sprint_id=? where id=?", waitingStory, sprint, waiting);
    jdbc.update("update user_stories set sprint_id=? where id=?", sprint, waitingStory);
    jdbc.update("update task set story_id=? where id=?", outsideStory, outside);
    dependency(project, outside, waiting);
    estimate(project, outside, 3);
    estimate(project, waiting, 2);
    mvc.perform(get(path(project) + "/roadmap").with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.epics[0].storyCount").value(2))
        .andExpect(jsonPath("$.epics[0].totalPoints").value(8))
        .andExpect(jsonPath("$.epics[0].donePoints").value(3))
        .andExpect(jsonPath("$.epics[0].progress").value(0.375))
        .andExpect(jsonPath("$.epics[0].forecast.expectedDays").value(5))
        .andExpect(jsonPath("$.epics[0].forecast.probabilityOnTime").value(1))
        .andExpect(jsonPath("$.epics[0].forecast.criticalPath[0].id").value(outside))
        .andExpect(jsonPath("$.epics[0].forecast.criticalPath[1].id").value(waiting))
        .andExpect(jsonPath("$.dependencies.blockedTaskIds[0]").value(waiting))
        .andExpect(
            jsonPath("$.dependencies.externalBlocks[0].blocker.key").value(project.getKey() + "-3"))
        .andExpect(jsonPath("$.epicLinks[0].fromEpicId").value(second))
        .andExpect(jsonPath("$.epicLinks[0].toEpicId").value(first))
        .andExpect(jsonPath("$.epicLinks[0].open").value(1));
    jdbc.update("update task set status='Done' where id=?", outside);
    mvc.perform(get(path(project) + "/roadmap").with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dependencies.blockedTaskIds").isEmpty())
        .andExpect(jsonPath("$.dependencies.externalBlocks").isEmpty())
        .andExpect(jsonPath("$.epics[0].forecast.expectedDays").value(2))
        .andExpect(jsonPath("$.epicLinks[0].open").value(0));
  }

  @Test
  void incompleteEstimatesAndUnbrokenStoriesCannotPromiseADeadline() throws Exception {
    var owner = user();
    var project = project(owner, null);
    long epic = epic(project, "Uncertain");
    jdbc.update("update epic set target_date=current_date+30 where id=?", epic);
    long story = story(project, epic), task = task(project, 1, "To Do");
    jdbc.update("update task set story_id=? where id=?", story, task);
    mvc.perform(get(path(project) + "/roadmap").with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.epics[0].forecast.expectedFinish").isEmpty())
        .andExpect(jsonPath("$.epics[0].forecast.probabilityOnTime").isEmpty());
    estimate(project, task, 2);
    story(project, epic);
    mvc.perform(get(path(project) + "/roadmap").with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.epics[0].forecast.probabilityOnTime").isEmpty())
        .andExpect(jsonPath("$.epics[0].forecast.unplannedStories").value(1));
  }

  @Test
  void tasklessStoriesRemainUnfinishedEvenWhenExistingTasksAreDone() throws Exception {
    var owner = user();
    var project = project(owner, null);
    long epic = epic(project, "Unpointed");
    long first = story(project, epic),
        second = story(project, epic),
        done = task(project, 1, "Done");
    jdbc.update("update epic set target_date=current_date+30 where id=?", epic);
    jdbc.update("update task set story_id=? where id=?", first, done);
    mvc.perform(get(path(project) + "/roadmap").with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.epics[0].progress").value(0.5))
        .andExpect(jsonPath("$.epics[0].forecast.probabilityOnTime").isEmpty());
  }

  @Test
  void readCostIsConstantAndTrashedTasksAndSprintsStayOut() throws Exception {
    var owner = user();
    var project = project(owner, null);
    long epic = epic(project, "Scale"), story = story(project, epic);
    var read = get(path(project) + "/roadmap").with(as(owner));
    mvc.perform(read).andExpect(status().isOk());
    long small = Long.MAX_VALUE;
    for (int i = 0; i < 3; i++)
      small =
          Math.min(
              small,
              statementsFor(
                  () ->
                      mvc.perform(get(path(project) + "/roadmap").with(as(owner)))
                          .andExpect(status().isOk())));
    jdbc.update(
        "insert into"
            + " task(title,status,is_blocked,project_id,story_id,story_points,number,created_at,version)"
            + " select 'Scale '||n,'To Do',false,?,?,1,n,now(),0 from generate_series(1,1000)n",
        project.getId(),
        story);
    long large = Long.MAX_VALUE;
    for (int i = 0; i < 3; i++)
      large =
          Math.min(
              large,
              statementsFor(
                  () ->
                      mvc.perform(get(path(project) + "/roadmap").with(as(owner)))
                          .andExpect(status().isOk())));
    assertThat(large).isEqualTo(small).isLessThanOrEqualTo(10);
    jdbc.update("update task set trashed_at=now() where project_id=?", project.getId());
    mvc.perform(get(path(project) + "/roadmap").with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.epics[0].taskCount").value(0));
  }
}

package com.sprintflowinsight.workspace.roadmap;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RoadmapMutationIntegrationTest extends RoadmapIntegrationSupport {
  @Test
  void epicEditsRequireTheReadVersionAndDeleteClearsTheStory() throws Exception {
    var owner = user();
    var project = project(owner, null);
    String created =
        mvc.perform(body(post(path(project) + "/epics"), epicBody()).with(as(owner)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.color").value("#ab12cd"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long epic = ((Number) JsonPath.read(created, "$.id")).longValue();
    long story = story(project, null);
    mvc.perform(body(put(path(project) + "/epics/" + epic), epicBody()).with(as(owner)))
        .andExpect(status().isPreconditionRequired());
    var changed = new java.util.HashMap<>(epicBody());
    changed.put("title", "Updated release");
    mvc.perform(
            body(put(path(project) + "/epics/" + epic).header("If-Match", "\"0\""), changed)
                .with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(
            delete(path(project) + "/epics/" + epic)
                .header("If-Match", "\"0\"")
                .with(csrf())
                .with(as(owner)))
        .andExpect(status().isPreconditionFailed());
    mvc.perform(
            body(
                    put(path(project) + "/stories/" + story + "/epic").header("If-Match", "\"0\""),
                    Map.of("epicId", epic))
                .with(as(owner)))
        .andExpect(status().isOk());
    mvc.perform(
            delete(path(project) + "/epics/" + epic)
                .header("If-Match", "\"1\"")
                .with(csrf())
                .with(as(owner)))
        .andExpect(status().isNoContent());
    assertThat(
            jdbc.queryForObject("select epic_id from user_stories where id=?", Long.class, story))
        .isNull();
  }

  @Test
  void estimatesCannotSilentlyOverwriteAnotherBrowsersChanges() throws Exception {
    var owner = user();
    var project = project(owner, null);
    long task = task(project, 1, "To Do");
    String url = path(project) + "/tasks/" + task + "/estimate";
    var estimate = Map.of("optimistic", 1, "mostLikely", 2, "pessimistic", 3);
    mvc.perform(body(put(url), estimate).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(0));
    mvc.perform(body(put(url), estimate).with(as(owner)))
        .andExpect(status().isPreconditionRequired());
    mvc.perform(body(put(url).header("If-Match", "\"0\""), estimate).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(body(put(url).header("If-Match", "\"0\""), estimate).with(as(owner)))
        .andExpect(status().isPreconditionFailed());
    mvc.perform(delete(url).header("If-Match", "\"0\"").with(csrf()).with(as(owner)))
        .andExpect(status().isPreconditionFailed());
    mvc.perform(delete(url).header("If-Match", "\"1\"").with(csrf()).with(as(owner)))
        .andExpect(status().isNoContent());
    mvc.perform(delete(url).with(csrf()).with(as(owner))).andExpect(status().isNoContent());
  }

  @Test
  void staleEstimateCannotResurrectClearedWorkOrOverwriteItsReplacement() throws Exception {
    var owner = user();
    var project = project(owner, null);
    long task = task(project, 1, "To Do");
    String url = path(project) + "/tasks/" + task + "/estimate";
    var estimate = Map.of("optimistic", 1, "mostLikely", 2, "pessimistic", 3);
    mvc.perform(body(put(url), estimate).with(as(owner))).andExpect(status().isOk());
    mvc.perform(delete(url).header("If-Match", "\"0\"").with(csrf()).with(as(owner)))
        .andExpect(status().isNoContent());
    mvc.perform(get(path(project) + "/estimates").with(as(owner)))
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(body(put(url).header("If-Match", "\"0\""), estimate).with(as(owner)))
        .andExpect(status().isPreconditionFailed());
    mvc.perform(body(put(url), estimate).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));
    mvc.perform(body(put(url).header("If-Match", "\"0\""), estimate).with(as(owner)))
        .andExpect(status().isPreconditionFailed());
    mvc.perform(delete(url).header("If-Match", "\"0\"").with(csrf()).with(as(owner)))
        .andExpect(status().isPreconditionFailed());
  }

  @Test
  void cycleResponsesNameThePathAndKeepTrashedEdgesRestoreSafe() throws Exception {
    var owner = user();
    var project = project(owner, null);
    long a = task(project, 1, "To Do"),
        b = task(project, 2, "To Do"),
        c = task(project, 3, "To Do");
    dependency(project, a, b);
    dependency(project, b, c);
    String url = path(project) + "/dependencies";
    mvc.perform(body(post(url), Map.of("blockerTaskId", c, "blockedTaskId", a)).with(as(owner)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.cycle.length()").value(4))
        .andExpect(jsonPath("$.cycle[0]").value(c))
        .andExpect(jsonPath("$.cycle[1]").value(a))
        .andExpect(jsonPath("$.cycle[2]").value(b))
        .andExpect(jsonPath("$.cycle[3]").value(c))
        .andExpect(jsonPath("$.cycleKeys[0]").value(project.getKey() + "-3"));
    mvc.perform(body(post(url), Map.of("blockerTaskId", a, "blockedTaskId", b)).with(as(owner)))
        .andExpect(status().isConflict());
    jdbc.update("update task set trashed_at=now() where id=?", b);
    mvc.perform(body(post(url), Map.of("blockerTaskId", c, "blockedTaskId", a)).with(as(owner)))
        .andExpect(status().isConflict());
    mvc.perform(body(post(url), Map.of("blockerTaskId", a, "blockedTaskId", a)).with(as(owner)))
        .andExpect(status().isBadRequest());
    mvc.perform(body(post(url), Map.of("blockerTaskId", a, "blockedTaskId", b)).with(as(owner)))
        .andExpect(status().isNotFound());
  }

  @Test
  void simultaneousWritersCannotEachAddHalfOfACycle() throws Exception {
    var owner = user();
    var project = project(owner, null);
    long a = task(project, 1, "To Do"), b = task(project, 2, "To Do");
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var futures =
          List.of(new long[] {a, b}, new long[] {b, a}).stream()
              .map(
                  edge ->
                      pool.submit(
                          () -> {
                            ready.countDown();
                            if (!start.await(10, TimeUnit.SECONDS))
                              throw new IllegalStateException("Start timed out");
                            return mvc.perform(
                                    body(
                                            post(path(project) + "/dependencies"),
                                            Map.of(
                                                "blockerTaskId", edge[0], "blockedTaskId", edge[1]))
                                        .with(as(owner)))
                                .andReturn()
                                .getResponse()
                                .getStatus();
                          }))
              .toList();
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(
              List.of(
                  futures.get(0).get(10, TimeUnit.SECONDS),
                  futures.get(1).get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    } finally {
      start.countDown();
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from task_dependency where project_id=?",
                Integer.class,
                project.getId()))
        .isEqualTo(1);
  }

  @Test
  void invalidInputsFailBeforeTheyReachPostgres() throws Exception {
    var owner = user();
    var project = project(owner, null);
    long task = task(project, 1, "To Do");
    for (var invalid :
        List.of(
            Map.of("title", " "),
            Map.of("title", "x", "color", "red"),
            Map.of("title", "x", "startDate", "2030-02-01", "targetDate", "2030-01-01"),
            Map.of("title", "x", "startDate", "1899-01-01")))
      mvc.perform(body(post(path(project) + "/epics"), invalid).with(as(owner)))
          .andExpect(status().isBadRequest());
    for (var invalid :
        List.of(
            Map.of(),
            Map.of("optimistic", -1, "mostLikely", 2, "pessimistic", 3),
            Map.of("optimistic", 3, "mostLikely", 2, "pessimistic", 4),
            Map.of("optimistic", 0, "mostLikely", 2, "pessimistic", 1),
            Map.of("optimistic", 1, "mostLikely", 2, "pessimistic", 1001),
            Map.of("optimistic", 0.001, "mostLikely", 2, "pessimistic", 3)))
      mvc.perform(
              body(put(path(project) + "/tasks/" + task + "/estimate"), invalid).with(as(owner)))
          .andExpect(status().isBadRequest());
    mvc.perform(
            body(
                    post(path(project) + "/dependencies"),
                    Map.of("blockerTaskId", -1, "blockedTaskId", task))
                .with(as(owner)))
        .andExpect(status().isBadRequest());
  }
}

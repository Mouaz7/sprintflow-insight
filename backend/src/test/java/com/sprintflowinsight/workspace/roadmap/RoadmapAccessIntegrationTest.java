package com.sprintflowinsight.workspace.roadmap;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.workspace.project.Project;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class RoadmapAccessIntegrationTest extends RoadmapIntegrationSupport {
  private record Mutation(MockHttpServletRequestBuilder request, int expected) {}

  @ParameterizedTest
  @ValueSource(strings = {"Owner", "Admin", "Member", "Viewer", "Outsider", "Archived", "Trashed"})
  void everyEndpointUsesTheProjectReadAndWriteRules(String role) throws Exception {
    User owner = user();
    Project project = project(owner, null);
    User caller = owner;
    if (List.of("Admin", "Member", "Viewer", "Outsider").contains(role)) {
      caller = user();
      if (!role.equals("Outsider")) member(project, caller, role);
    }
    long epic = epic(project, "Initial");
    long story = story(project, null);
    long first = task(project, 1, "To Do"),
        second = task(project, 2, "To Do"),
        third = task(project, 3, "To Do");
    long dependency = dependency(project, first, second);
    estimate(project, first, 1);
    if (role.equals("Archived")) {
      project.setArchivedAt(Instant.now());
      projects.save(project);
    }
    if (role.equals("Trashed")) {
      project.setTrashedAt(Instant.now());
      projects.save(project);
    }
    int read = role.equals("Outsider") ? 403 : role.equals("Trashed") ? 404 : 200;
    for (String resource : List.of("epics", "dependencies", "estimates", "roadmap"))
      mvc.perform(get(path(project) + "/" + resource).with(as(caller)))
          .andExpect(status().is(read));
    int denied = role.equals("Trashed") ? 404 : role.equals("Archived") ? 409 : 403;
    boolean write = List.of("Owner", "Admin", "Member").contains(role);
    var mutations =
        List.of(
            new Mutation(body(post(path(project) + "/epics"), epicBody()), 201),
            new Mutation(
                body(put(path(project) + "/epics/" + epic).header("If-Match", "\"0\""), epicBody()),
                200),
            new Mutation(
                body(
                    put(path(project) + "/stories/" + story + "/epic").header("If-Match", "\"0\""),
                    Map.of("epicId", epic)),
                200),
            new Mutation(
                body(
                    post(path(project) + "/dependencies"),
                    Map.of("blockerTaskId", first, "blockedTaskId", third)),
                201),
            new Mutation(delete(path(project) + "/dependencies/" + dependency).with(csrf()), 204),
            new Mutation(
                body(
                    put(path(project) + "/tasks/" + first + "/estimate")
                        .header("If-Match", "\"0\""),
                    Map.of("optimistic", 1, "mostLikely", 2, "pessimistic", 3)),
                200),
            new Mutation(
                delete(path(project) + "/tasks/" + first + "/estimate")
                    .header("If-Match", "\"1\"")
                    .with(csrf()),
                204),
            new Mutation(
                delete(path(project) + "/epics/" + epic).header("If-Match", "\"1\"").with(csrf()),
                204));
    for (var mutation : mutations)
      mvc.perform(mutation.request().with(as(caller)))
          .andExpect(status().is(write ? mutation.expected() : denied));
  }

  @Test
  void anonymousReadersMustSignInAndEntitiesCannotCrossProjects() throws Exception {
    User owner = user();
    Project own = project(owner, null), foreign = project(user(), null);
    long foreignEpic = epic(foreign, "Private"), ownStory = story(own, null);
    long ownTask = task(own, 1, "To Do"), foreignTask = task(foreign, 1, "To Do");
    for (String resource : List.of("epics", "dependencies", "estimates", "roadmap"))
      mvc.perform(get(path(own) + "/" + resource)).andExpect(status().isUnauthorized());
    mvc.perform(
            body(put(path(own) + "/epics/" + foreignEpic).header("If-Match", "\"0\""), epicBody())
                .with(as(owner)))
        .andExpect(status().isNotFound());
    mvc.perform(
            body(
                    put(path(own) + "/stories/" + ownStory + "/epic").header("If-Match", "\"0\""),
                    Map.of("epicId", foreignEpic))
                .with(as(owner)))
        .andExpect(status().isNotFound());
    mvc.perform(
            body(
                    post(path(own) + "/dependencies"),
                    Map.of("blockerTaskId", foreignTask, "blockedTaskId", ownTask))
                .with(as(owner)))
        .andExpect(status().isNotFound());
    mvc.perform(
            body(
                    put(path(own) + "/tasks/" + foreignTask + "/estimate"),
                    Map.of("optimistic", 1, "mostLikely", 2, "pessimistic", 3))
                .with(as(owner)))
        .andExpect(status().isNotFound());
    mvc.perform(
            delete(path(own) + "/epics/" + foreignEpic)
                .header("If-Match", "\"0\"")
                .with(csrf())
                .with(as(owner)))
        .andExpect(status().isNotFound());
    long foreignDep = dependency(foreign, foreignTask, task(foreign, 2, "To Do"));
    mvc.perform(delete(path(own) + "/dependencies/" + foreignDep).with(csrf()).with(as(owner)))
        .andExpect(status().isNotFound());
  }
}

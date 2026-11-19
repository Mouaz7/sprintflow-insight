package com.sprintflowinsight.workspace.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sprintflowinsight.support.ApiIntegrationTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class DashboardIntegrationTest extends ApiIntegrationTest {
  @Autowired DashboardRepository layouts;
  private static final String ONE = "{\"widgets\":[{\"type\":\"MY_TASKS\",\"wide\":true}]}";

  @ParameterizedTest
  @ValueSource(strings = {"Owner", "Admin", "Member", "Viewer", "Outsider", "Archived", "Trashed"})
  void everyEndpointEnforcesMembershipAndLifecycle(String role) throws Exception {
    var owner = user();
    var caller =
        role.equals("Owner") || role.equals("Archived") || role.equals("Trashed") ? owner : user();
    var project = project(owner, null);
    if (List.of("Admin", "Member", "Viewer").contains(role)) member(project, caller, role);
    if (role.equals("Archived")) project.setArchivedAt(Instant.now());
    if (role.equals("Trashed")) project.setTrashedAt(Instant.now());
    projects.save(project);
    String path = "/api/projects/" + project.getId() + "/dashboard";
    int read = role.equals("Outsider") ? 403 : role.equals("Trashed") ? 404 : 200;
    int write = role.equals("Archived") ? 409 : read;
    mvc.perform(get(path).with(as(caller))).andExpect(status().is(read));
    mvc.perform(
            put(path)
                .with(as(caller))
                .with(csrf())
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ONE))
        .andExpect(status().is(write));
  }

  @Test
  void privateLayoutsPersistOrderedWidthsWithoutChangingOtherAccountsOrProjects() throws Exception {
    var owner = user();
    var peer = user();
    var p = project(owner, peer);
    var other = project(owner, null);
    String path = "/api/projects/" + p.getId() + "/dashboard";
    long count = layouts.count();
    mvc.perform(get(path).with(as(owner)))
        .andExpect(jsonPath("$.version").value(0))
        .andExpect(jsonPath("$.widgets.length()").value(6));
    assertThat(layouts.count()).isEqualTo(count);
    mvc.perform(
            put(path)
                .with(as(owner))
                .with(csrf())
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ONE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    mvc.perform(get(path).with(as(owner)))
        .andExpect(jsonPath("$.widgets[0].type").value("MY_TASKS"))
        .andExpect(jsonPath("$.widgets[0].wide").value(true));
    mvc.perform(get(path).with(as(peer)))
        .andExpect(jsonPath("$.version").value(0))
        .andExpect(jsonPath("$.widgets.length()").value(6));
    mvc.perform(get("/api/projects/" + other.getId() + "/dashboard").with(as(owner)))
        .andExpect(jsonPath("$.version").value(0));
    mvc.perform(
            put(path)
                .with(as(owner))
                .with(csrf())
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ONE))
        .andExpect(status().isPreconditionFailed());
    mvc.perform(
            put(path)
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ONE))
        .andExpect(status().isPreconditionRequired());
    mvc.perform(
            put(path)
                .with(as(owner))
                .header("If-Match", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ONE))
        .andExpect(status().isForbidden());
    // An intentionally empty layout is saved, rather than mistaken for the default.
    mvc.perform(
            put(path)
                .with(as(owner))
                .with(csrf())
                .header("If-Match", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"widgets\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.widgets").isEmpty());
    // Cascading cleanup on account deletion.
    users.delete(owner);
    assertThat(layouts.findByProjectIdAndUserId(p.getId(), owner.getId())).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"widgets\":null}",
        "{\"widgets\":[null]}",
        "{\"widgets\":[{}]}",
        "{\"widgets\":[{\"type\":\"UNKNOWN\",\"wide\":false}]}",
        "{\"widgets\":[{\"type\":\"WIP\"}]}",
        "{\"widgets\":[{\"type\":\"WIP\",\"wide\":false},{\"type\":\"WIP\",\"wide\":true}]}"
      })
  void invalidLayoutsAreRejected(String body) throws Exception {
    var owner = user();
    var p = project(owner, null);
    mvc.perform(
            put("/api/projects/" + p.getId() + "/dashboard")
                .with(as(owner))
                .with(csrf())
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void oversizedLayoutsAndUnauthenticatedAccessAreRejected() throws Exception {
    var owner = user();
    var p = project(owner, null);
    String path = "/api/projects/" + p.getId() + "/dashboard";
    String widgets =
        java.util.stream.IntStream.range(0, 7)
            .mapToObj(i -> "{\"type\":\"WIP\",\"wide\":false}")
            .collect(java.util.stream.Collectors.joining(","));
    mvc.perform(
            put(path)
                .with(as(owner))
                .with(csrf())
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"widgets\":[" + widgets + "]}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
  }
}

package com.sprintflowinsight.realtime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.support.ApiIntegrationTest;
import com.sprintflowinsight.workspace.project.Project;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** GET /api/projects/{id}/events for every role, outsiders, archived and trashed projects. */
class RealtimeEventsAccessIntegrationTest extends ApiIntegrationTest {
  private static String events(Project project) {
    return "/api/projects/" + project.getId() + "/events";
  }

  @ParameterizedTest
  @ValueSource(strings = {"Owner", "Admin", "Member", "Viewer"})
  void everyRoleCanReadTheEventLog(String role) throws Exception {
    User owner = user();
    Project project = project(owner, null);
    User reader = owner;
    if (!role.equals("Owner")) {
      reader = user();
      member(project, reader, role);
    }
    mvc.perform(get(events(project)).param("after", "0").with(as(reader)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.projectId").value(project.getId()))
        .andExpect(jsonPath("$.latestSeq").value(0))
        .andExpect(jsonPath("$.resync").value(false))
        .andExpect(jsonPath("$.events").isEmpty());
  }

  @Test
  void outsidersAreForbiddenAndAnonymousCallersMustSignIn() throws Exception {
    Project project = project(user(), null);
    mvc.perform(get(events(project)).with(as(user())))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mvc.perform(get(events(project))).andExpect(status().isUnauthorized());
  }

  @Test
  void archivedProjectsStayReadableButTrashedOnesAreNotFound() throws Exception {
    User owner = user();
    User viewer = user();
    Project archived = project(owner, null);
    member(archived, viewer, "Viewer");
    archived.setArchivedAt(Instant.now());
    projects.save(archived);
    mvc.perform(get(events(archived)).with(as(viewer))).andExpect(status().isOk());

    Project trashed = project(owner, null);
    trashed.setTrashedAt(Instant.now());
    projects.save(trashed);
    mvc.perform(get(events(trashed)).with(as(owner))).andExpect(status().isNotFound());
    mvc.perform(get("/api/projects/999999999/events").with(as(owner)))
        .andExpect(status().isNotFound());
  }

  @Test
  void negativeOrMalformedPositionsAreRejected() throws Exception {
    User owner = user();
    Project project = project(owner, null);
    mvc.perform(get(events(project)).param("after", "-1").with(as(owner)))
        .andExpect(status().isBadRequest());
    mvc.perform(get(events(project)).param("after", "abc").with(as(owner)))
        .andExpect(status().isBadRequest());
  }
}

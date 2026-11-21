package com.sprintflowinsight.crdt;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.support.HttpFlowTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

class CrdtHttpIntegrationTest extends HttpFlowTest {
  @Autowired CrdtService collaboration;

  @Test
  void checksDocumentScopeAndExistingPermissionsForEveryRole() {
    registerTestUser();
    String owner = sessionCookie;
    long project = createProject();
    long task = createTask(project);
    long sprint = createSprint(project);
    long note =
        id(
            request(
                HttpMethod.POST,
                "/api/projects/" + project + "/retrospective",
                Map.of("sprintId", sprint, "category", "well", "message", "Original note")));
    String taskRoom = room(project, "TASK", task);
    String noteRoom = room(project, "RETROSPECTIVE", note);
    assertThat(request(HttpMethod.GET, taskRoom + "/metadata", null).getBody())
        .containsEntry("active", false);

    for (String role : new String[] {"Viewer", "Member", "Admin"}) {
      registerTestUser();
      String collaborator = sessionCookie;
      long user = userRepository.findByEmail(testEmail).orElseThrow().getId();
      sessionCookie = owner;
      assertThat(
              request(
                      HttpMethod.POST,
                      "/api/projects/" + project + "/members",
                      Map.of("email", testEmail, "role", role))
                  .getStatusCode())
          .isEqualTo(HttpStatus.OK);
      sessionCookie = collaborator;
      assertThat(request(HttpMethod.GET, taskRoom, null).getBody())
          .containsEntry("editable", !role.equals("Viewer"));
      assertThat(request(HttpMethod.GET, noteRoom, null).getBody())
          .containsEntry("editable", role.equals("Admin"));
      assertThat(request(HttpMethod.POST, taskRoom, null).getStatusCode())
          .isEqualTo(role.equals("Viewer") ? HttpStatus.FORBIDDEN : HttpStatus.OK);
      assertThat(request(HttpMethod.POST, noteRoom, null).getStatusCode())
          .isEqualTo(role.equals("Admin") ? HttpStatus.OK : HttpStatus.FORBIDDEN);
      if (!role.equals("Admin")) {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () ->
                    collaboration.appendFromSocket(
                        project,
                        CrdtTarget.RETROSPECTIVE,
                        note,
                        user,
                        UUID.randomUUID(),
                        new byte[] {1, 0}))
            .isInstanceOf(com.sprintflowinsight.common.error.ApiException.class)
            .hasMessageContaining("permission");
      }
      if (role.equals("Member")) {
        long ownNote =
            id(
                request(
                    HttpMethod.POST,
                    "/api/projects/" + project + "/retrospective",
                    Map.of("sprintId", sprint, "category", "well", "message", "My own note")));
        assertThat(
                request(HttpMethod.POST, room(project, "RETROSPECTIVE", ownNote), null).getBody())
            .containsEntry("editable", true);
      }
    }

    registerTestUser();
    assertThat(request(HttpMethod.GET, taskRoom, null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(request(HttpMethod.GET, taskRoom + "/metadata", null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    long other = createProject();
    assertThat(request(HttpMethod.GET, room(other, "TASK", task), null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    sessionCookie = owner;
    assertThat(request(HttpMethod.POST, noteRoom, null).getBody()).containsEntry("editable", true);
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/projects/" + project + "/archive",
                    Map.of("archived", true))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(request(HttpMethod.GET, taskRoom, null).getBody())
        .containsEntry("active", true)
        .containsEntry("editable", false);
    assertThat(request(HttpMethod.POST, taskRoom, null).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void projectsOnlyTheLatestConfirmedRevisionAndPreservesOrdinaryMetadataWrites() {
    registerTestUser();
    long user = userRepository.findByEmail(testEmail).orElseThrow().getId();
    long project = createProject();
    long task = createTask(project);
    String room = room(project, "TASK", task);
    assertThat(request(HttpMethod.POST, room, null).getBody())
        .containsEntry("initialText", "Original");
    assertThat(request(HttpMethod.GET, room + "/metadata", null).getBody())
        .containsEntry("active", true);
    assertThat(
            request(HttpMethod.POST, room + "/projection", Map.of("revision", 1, "text", "Future"))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    UUID update = UUID.randomUUID();
    byte[] payload = {0, 0};
    assertThat(
            collaboration.appendFromSocket(project, CrdtTarget.TASK, task, user, update, payload))
        .isEqualTo(new CrdtService.Append(1, false));
    assertThat(
            collaboration.appendFromSocket(project, CrdtTarget.TASK, task, user, update, payload))
        .isEqualTo(new CrdtService.Append(1, true));
    assertThat(request(HttpMethod.GET, room + "?after=1", null).getBody())
        .containsEntry("revision", 1)
        .containsEntry("updates", java.util.List.of());
    assertThat(
            request(HttpMethod.POST, room + "/projection", Map.of("revision", 1, "text", "Shared"))
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(
            request(
                    HttpMethod.POST,
                    room + "/projection",
                    Map.of("revision", 1, "text", "Old duplicate"))
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(request(HttpMethod.GET, "/api/tasks/" + task, null).getBody())
        .containsEntry("description", "Shared");
    var updateTask =
        Map.<String, Object>of(
            "projectId",
            project,
            "title",
            "Metadata saved",
            "status",
            "To Do",
            "description",
            "Ordinary overwrite");
    assertThat(request(HttpMethod.PUT, "/api/tasks/" + task, updateTask).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    var metadata = new java.util.HashMap<>(updateTask);
    metadata.put("description", "Shared");
    assertThat(request(HttpMethod.PUT, "/api/tasks/" + task, metadata).getBody())
        .containsEntry("title", "Metadata saved")
        .containsEntry("description", "Shared");
  }

  private long createProject() {
    return id(
        request(
            HttpMethod.POST, "/api/projects", Map.of("name", "Shared text " + UUID.randomUUID())));
  }

  private long createTask(long project) {
    return id(
        request(
            HttpMethod.POST,
            "/api/tasks",
            Map.of(
                "projectId",
                project,
                "title",
                "Shared task",
                "description",
                "Original",
                "status",
                "To Do")));
  }

  private long createSprint(long project) {
    return id(
        request(
            HttpMethod.POST,
            "/api/sprints",
            Map.of(
                "projectId",
                project,
                "name",
                "Shared note sprint",
                "status",
                "Planned",
                "startDate",
                "2026-10-01",
                "endDate",
                "2026-10-14")));
  }

  private static String room(long project, String kind, long id) {
    return "/api/projects/" + project + "/collaboration/" + kind + "/" + id;
  }
}

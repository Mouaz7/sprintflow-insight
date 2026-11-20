package com.sprintflowinsight.workspace.transfer;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.support.ApiIntegrationTest;
import com.sprintflowinsight.workspace.project.Project;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class ImportIntegrationTest extends ApiIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  Map<String, Object> request(String content) {
    var m = new LinkedHashMap<String, Object>();
    m.put("format", "CSV");
    m.put("content", content);
    m.put("mapping", Map.of());
    m.put("statuses", Map.of());
    m.put("truncateText", false);
    m.put("preserveHistory", false);
    return m;
  }

  String postPreview(Project p, User u, Map<String, Object> body) throws Exception {
    return mvc.perform(
            post("/api/projects/" + p.getId() + "/imports/preview")
                .with(as(u))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  void previewIsReadOnlyCommitIsAtomicAndExactReplayDoesNotCreateAgain() throws Exception {
    var owner = user();
    var p = project(owner, null);
    var req = request("title,status,sourceKey\nOne,To Do,EXT-1\nTwo,Done,EXT-2\n");
    var preview = json.readTree(postPreview(p, owner, req));
    assertThat(preview.path("canCommit").asBoolean()).isTrue();
    for (String table :
        List.of(
            "task", "activity_event", "task_status_history", "import_batch", "admin_audit_event"))
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from " + table + " where project_id=?",
                  Integer.class,
                  p.getId()))
          .isZero();
    req.put("expectedFingerprint", preview.path("fingerprint").asString());
    String path = "/api/projects/" + p.getId() + "/imports";
    var saved =
        mvc.perform(
                post(path)
                    .with(as(owner))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(json.readTree(saved.getResponse().getContentAsString()).path("createdCount").asInt())
        .isEqualTo(2);
    mvc.perform(
            post(path)
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.replayed").value(true));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from task where project_id=?", Integer.class, p.getId()))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from admin_audit_event where project_id=?",
                Integer.class,
                p.getId()))
        .isEqualTo(1);
    mvc.perform(get(path).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void invalidRowsAndChangedSchemaOrMembershipDoNotPartiallyImport() throws Exception {
    var owner = user();
    var peer = user();
    var p = project(owner, peer);
    var req = request("title,status\nGood,To Do\nBad,UNKNOWN");
    var preview = json.readTree(postPreview(p, owner, req));
    assertThat(preview.path("canCommit").asBoolean()).isFalse();
    req.put("expectedFingerprint", preview.path("fingerprint").asString());
    String path = "/api/projects/" + p.getId() + "/imports";
    mvc.perform(
            post(path)
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
        .andExpect(status().isBadRequest());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from task where project_id=?", Integer.class, p.getId()))
        .isZero();
    req = request("title\nGood");
    preview = json.readTree(postPreview(p, owner, req));
    req.put("expectedFingerprint", preview.path("fingerprint").asString());
    jdbc.update(
        "delete from project_member where project_id=? and user_id=?", p.getId(), peer.getId());
    mvc.perform(
            post(path)
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
        .andExpect(status().isConflict());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from import_batch where project_id=?", Integer.class, p.getId()))
        .isZero();
  }

  @Test
  void historyAndCustomDecimalsStayExactAndDuplicatesAreSkipped() throws Exception {
    var owner = user();
    var p = project(owner, null);
    var field =
        mvc.perform(
                post("/api/projects/" + p.getId() + "/custom-fields")
                    .with(as(owner))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Cost\",\"kind\":\"NUMBER\"}"))
            .andExpect(status().isOk())
            .andReturn();
    long fieldId = json.readTree(field.getResponse().getContentAsString()).path("id").asLong();
    var req =
        request(
            "title,status,sourceKey,createdAt,startedAt,completedAt,Cost\n"
                + "Past,Done,OLD-1,2020-01-01T00:00:00Z,2020-01-02T00:00:00Z,2020-01-03T00:00:00Z,9007199254740993.123456");
    req.put("preserveHistory", true);
    req.put(
        "mapping",
        Map.of(
            "title",
            "title",
            "status",
            "status",
            "sourceKey",
            "sourceKey",
            "createdAt",
            "createdAt",
            "startedAt",
            "startedAt",
            "completedAt",
            "completedAt",
            "custom:" + fieldId,
            "Cost"));
    var preview = json.readTree(postPreview(p, owner, req));
    assertThat(preview.path("canCommit").asBoolean()).isTrue();
    req.put("expectedFingerprint", preview.path("fingerprint").asString());
    mvc.perform(
            post("/api/projects/" + p.getId() + "/imports")
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "select number_value::text from task_custom_value where project_id=?",
                String.class,
                p.getId()))
        .isEqualTo("9007199254740993.123456");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from task_status_history where project_id=? and"
                    + " changed_at<'2021-01-01'",
                Integer.class,
                p.getId()))
        .isEqualTo(3);
    req.put("content", req.get("content") + "\nNew,To Do,OLD-2,2020-01-01T00:00:00Z,,,,");
    req.put("preserveHistory", false);
    // Use a new valid file with an existing external key, rather than silently updating it.
    req.put(
        "content",
        "title,status,sourceKey,createdAt,startedAt,completedAt,Cost\n"
            + "Changed title,Done,OLD-1,2020-01-01T00:00:00Z,,2020-01-03T00:00:00Z,1");
    preview = json.readTree(postPreview(p, owner, req));
    assertThat(preview.path("skipCount").asInt()).isEqualTo(1);
  }

  @Test
  void roleArchiveTrashAndCsrfBoundaries() throws Exception {
    for (String role : List.of("Owner", "Admin", "Member", "Viewer", "Outsider")) {
      var owner = user();
      var caller = role.equals("Owner") ? owner : user();
      var p = project(owner, null);
      if (!role.equals("Owner") && !role.equals("Outsider")) member(p, caller, role);
      String base = "/api/projects/" + p.getId();
      String body = json.writeValueAsString(request("title\nOne"));
      int expected = Set.of("Viewer", "Outsider").contains(role) ? 403 : 200;
      mvc.perform(
              post(base + "/imports/preview")
                  .with(as(caller))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().is(expected));
      var commitRequest = request("title\nOne");
      if (expected == 200) {
        var view = json.readTree(postPreview(p, caller, commitRequest));
        commitRequest.put("expectedFingerprint", view.path("fingerprint").asString());
      }
      mvc.perform(
              post(base + "/imports")
                  .with(as(caller))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsString(commitRequest)))
          .andExpect(status().is(expected));
      var historical = request("title,createdAt\nOne,2020-01-01T00:00:00Z");
      historical.put("preserveHistory", true);
      mvc.perform(
              post(base + "/imports/preview")
                  .with(as(caller))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsString(historical)))
          .andExpect(status().is(Set.of("Owner", "Admin").contains(role) ? 200 : 403));
      mvc.perform(get(base + "/imports").with(as(caller)))
          .andExpect(status().is(role.equals("Outsider") ? 403 : 200));
      mvc.perform(get(base + "/audit").with(as(caller)))
          .andExpect(status().is(Set.of("Owner", "Admin").contains(role) ? 200 : 403));
      mvc.perform(
              post(base + "/imports")
                  .with(as(caller))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isForbidden());
      jdbc.update("update projects set archived_at=now() where id=?", p.getId());
      mvc.perform(
              post(base + "/imports/preview")
                  .with(as(caller))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().is(Set.of("Viewer", "Outsider").contains(role) ? 403 : 409));
      jdbc.update("update projects set trashed_at=now() where id=?", p.getId());
      mvc.perform(get(base + "/imports").with(as(caller))).andExpect(status().isNotFound());
    }
  }
}

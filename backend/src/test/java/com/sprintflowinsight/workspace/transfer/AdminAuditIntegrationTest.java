package com.sprintflowinsight.workspace.transfer;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sprintflowinsight.common.audit.AuditIntegrityService;
import com.sprintflowinsight.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminAuditIntegrationTest extends ApiIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired AuditIntegrityService integrity;

  @Test
  void administrativeChangesAreAuditedWithoutContentAndSurviveProjectDeletion() throws Exception {
    var owner = user();
    var peer = user();
    var p = project(owner, peer);
    String base = "/api/projects/" + p.getId();
    mvc.perform(
            post(base + "/members")
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + peer.getEmail() + "\",\"role\":\"Viewer\"}"))
        .andExpect(status().isOk());
    mvc.perform(
            put(base + "/archive")
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"archived\":true}"))
        .andExpect(status().isOk());
    mvc.perform(get(base + "/audit?size=1").with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.nextBefore").isNumber());
    var entries =
        jdbc.queryForList(
            "select action,details::text,target_id from admin_audit_event where project_id=?",
            p.getId());
    assertThat(entries.toString())
        .contains("MEMBER_UPDATED", "PROJECT_ARCHIVED")
        .doesNotContain(peer.getEmail(), owner.getEmail());
    mvc.perform(
            delete(base)
                .with(as(owner))
                .with(csrf())
                .header(
                    "If-Match",
                    "\"" + projects.findById(p.getId()).orElseThrow().getVersion() + "\""))
        .andExpect(status().isNoContent());
    assertThat(
            jdbc.queryForList(
                "select action from admin_audit_event where project_id=?", String.class, p.getId()))
        .contains("PROJECT_PURGED", "MEMBER_UPDATED");
  }

  @Test
  void retentionDeletesOnlyTheCheckpointedExpiredPrefix() {
    var actor = user();
    long projectId = project(actor, null).getId();
    long old =
        jdbc.queryForObject(
            "insert into"
                + " admin_audit_event(project_id,actor_user_id,actor_role,action,target_type,created_at)"
                + " values(?,?,'Owner','TEST','project',now()-interval '91 days') returning id",
            Long.class,
            projectId,
            actor.getId());
    long id =
        jdbc.queryForObject(
            "insert into admin_audit_event(project_id,actor_user_id,actor_role,action,target_type)"
                + " values(?,?,'Owner','TEST','project') returning id",
            Long.class,
            projectId,
            actor.getId());
    assertThatThrownBy(
            () -> jdbc.update("update admin_audit_event set action='FORGED' where id=?", id))
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.update("delete from admin_audit_event where id=?", id))
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.update("delete from admin_audit_event where id=?", old))
        .hasMessageContaining("append-only");
    jdbc.queryForObject("select prune_audit_history(10000)", Integer.class);
    assertThat(
            jdbc.queryForList(
                "select id from admin_audit_event where project_id=?", Long.class, projectId))
        .containsExactly(id);
    var verification = integrity.verify(projectId);
    assertThat(verification.valid()).isTrue();
    assertThat(verification.floorPosition()).isEqualTo(1);
    assertThat(verification.checkedEvents()).isEqualTo(1);
  }

  @Test
  void failedAdministrativeChangeDoesNotLeaveAnAuditRecord() throws Exception {
    var owner = user();
    var p = project(owner, null);
    mvc.perform(
            delete("/api/projects/" + p.getId() + "/activity/9999999").with(as(owner)).with(csrf()))
        .andExpect(status().isNotFound());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from admin_audit_event where project_id=?",
                Integer.class,
                p.getId()))
        .isZero();
  }
}

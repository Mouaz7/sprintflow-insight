package com.sprintflowinsight.portability;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sprintflowinsight.support.ApiIntegrationTest;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AccountExportIntegrationTest extends ApiIntegrationTest {
  @Autowired JdbcTemplate jdbc;

  @Test
  void archiveIncludesOnlySessionAccountAndAuthorizedDataWithoutCredentials() throws Exception {
    var owner = user();
    var other = user();
    owner.setDisplayName("Exported Person");
    owner.setRecoveryCodeHash("NEVER_EXPORT_RECOVERY_HASH");
    owner.setPassword("NEVER_EXPORT_PASSWORD");
    users.saveAndFlush(owner);
    var mine = project(owner, null);
    var theirs = project(other, null);
    jdbc.update(
        "insert into"
            + " task(project_id,number,title,status,is_blocked,story_points,custom_fields_revision)"
            + " values(?,1,'OWN_PROJECT_TASK','To Do',false,0,0)",
        mine.getId());
    jdbc.update(
        "insert into"
            + " task(project_id,number,title,status,is_blocked,story_points,custom_fields_revision)"
            + " values(?,1,'FOREIGN_PRIVATE_TASK','To Do',false,0,0)",
        theirs.getId());
    byte[] bytes =
        mvc.perform(post("/api/account/export").with(as(owner)).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/zip"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    var files = unzip(bytes);
    assertThat(files)
        .containsKeys("manifest.json", "account.json", "tasks.json", "own-poker-votes.json");
    String all = String.join("", files.values());
    assertThat(all)
        .contains("Exported Person", "OWN_PROJECT_TASK")
        .doesNotContain(
            "NEVER_EXPORT_RECOVERY_HASH",
            "NEVER_EXPORT_PASSWORD",
            "FOREIGN_PRIVATE_TASK",
            "token_version",
            "recovery_code_hash",
            "storage_key");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from admin_audit_event where actor_user_id=? and"
                    + " action='ACCOUNT_EXPORTED'",
                Integer.class,
                owner.getId()))
        .isEqualTo(1);
  }

  @Test
  void anonymousAndMissingCsrfCannotExport() throws Exception {
    mvc.perform(post("/api/account/export").with(csrf())).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/account/export").with(as(user()))).andExpect(status().isForbidden());
  }

  @Test
  void hiddenVotesAndFormerProjectContentStayPrivate() throws Exception {
    var owner = user();
    var peer = user();
    var p = project(owner, peer);
    long story =
        jdbc.queryForObject(
            "insert into"
                + " user_stories(project_id,title,acceptance_criteria,story_points,priority_id,version)"
                + " values(?,'Vote story','Criteria',3,2,0) returning id",
            Long.class,
            p.getId());
    long poker =
        jdbc.queryForObject(
            "insert into"
                + " poker_session(project_id,story_id,facilitator_user_id,deck,cards,status,created_at,updated_at)"
                + " values(?,?,?,'FIBONACCI','[1,3,8]','VOTING',now(),now()) returning id",
            Long.class,
            p.getId(),
            story,
            owner.getId());
    jdbc.update(
        "insert into poker_vote(session_id,round,user_id,card,voted_at)"
            + " values(?,1,?,'3',now()),(?,1,?,'8',now())",
        poker,
        owner.getId(),
        poker,
        peer.getId());
    var former = project(peer, owner);
    long task =
        jdbc.queryForObject(
            "insert into"
                + " task(project_id,number,title,status,is_blocked,story_points,custom_fields_revision)"
                + " values(?,1,'FORMER_PROJECT_TASK','To Do',false,0,0) returning id",
            Long.class,
            former.getId());
    jdbc.update(
        "insert into task_comment(project_id,task_id,author_email,message,created_at,version)"
            + " values(?,?,?,'OWN_FORMER_CONTRIBUTION',now(),0),(?,?,?,'PEER_PRIVATE_CONTRIBUTION',now(),0)",
        former.getId(),
        task,
        owner.getEmail(),
        former.getId(),
        task,
        peer.getEmail());
    jdbc.update(
        "delete from project_member where project_id=? and user_id=?",
        former.getId(),
        owner.getId());
    var files =
        unzip(
            mvc.perform(post("/api/account/export").with(as(owner)).with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray());
    assertThat(files.get("revealed-poker-votes.json")).isEqualTo("[]");
    assertThat(files.get("own-poker-votes.json"))
        .contains("\"card\":\"3\"")
        .doesNotContain("\"card\":\"8\"");
    assertThat(files.get("own-comments.json")).contains("OWN_FORMER_CONTRIBUTION");
    assertThat(String.join("", files.values()))
        .doesNotContain("PEER_PRIVATE_CONTRIBUTION", "FORMER_PROJECT_TASK");
  }

  @Test
  void unavailableStoredAttachmentAbortsRatherThanReturningPartialArchive() throws Exception {
    var owner = user();
    var p = project(owner, null);
    long task =
        jdbc.queryForObject(
            "insert into"
                + " task(project_id,number,title,status,is_blocked,story_points,custom_fields_revision)"
                + " values(?,1,'File task','To Do',false,0,0) returning id",
            Long.class,
            p.getId());
    jdbc.update(
        "insert into task_attachment(project_id,task_id,name,storage_key,created_at)"
            + " values(?,?,'Missing PDF',?,now())",
        p.getId(),
        task,
        java.util.UUID.randomUUID() + ".pdf");
    mvc.perform(post("/api/account/export").with(as(owner)).with(csrf()))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from admin_audit_event where actor_user_id=?",
                Integer.class,
                owner.getId()))
        .isZero();
  }

  static Map<String, String> unzip(byte[] bytes) throws IOException {
    var result = new LinkedHashMap<String, String>();
    try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry e;
      while ((e = zip.getNextEntry()) != null)
        result.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
    }
    return result;
  }
}

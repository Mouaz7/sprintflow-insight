package com.sprintflowinsight.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.support.HttpFlowTest;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;

class AuditIntegrityIntegrationTest extends HttpFlowTest {
  @Autowired AuditService audit;
  @Autowired AuditIntegrityService integrity;

  @Test
  void persistsTheChainAndRejectsTamperingWithEventsOrTheDailyAnchor() {
    registerTestUser();
    long user = userRepository.findByEmail(testEmail).orElseThrow().getId();
    long project = project();
    var transaction = new TransactionTemplate(transactions);
    transaction.executeWithoutResult(
        status -> {
          audit.record(
              project,
              user,
              "Owner",
              "PROJECT_UPDATED",
              "project",
              project,
              Map.of("field", "name"));
          audit.record(
              project, user, "Owner", "MEMBER_UPDATED", "member", 5, Map.of("role", "Member"));
        });
    LocalDate date = LocalDate.of(2026, 11, 21);
    integrity.anchor(date);
    integrity.anchor(date);
    var report = integrity.verify(project);
    assertThat(report.valid()).isTrue();
    assertThat(report.checkedEvents()).isEqualTo(2);
    assertThat(report.dailyAnchor().hash()).isEqualTo(report.headHash());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from admin_audit_anchor where scope_id=?", Integer.class, project))
        .isEqualTo(1);

    transaction.executeWithoutResult(
        status -> {
          status.setRollbackOnly();
          jdbc.execute("alter table admin_audit_event disable trigger admin_audit_append_only");
          jdbc.update(
              "update admin_audit_event set details='{}'::jsonb where project_id=? and"
                  + " chain_position=1",
              project);
          var failed = integrity.verify(project);
          assertThat(failed.valid()).isFalse();
          assertThat(failed.invalidPosition()).isEqualTo(1);
          integrity.anchor(date.plusDays(1));
          assertThat(
                  jdbc.queryForObject(
                      "select count(*) from admin_audit_anchor where scope_id=?",
                      Integer.class,
                      project))
              .isEqualTo(1);
        });
    transaction.executeWithoutResult(
        status -> {
          status.setRollbackOnly();
          jdbc.update(
              "update admin_audit_anchor set authenticated_proof='changed' where scope_id=?",
              project);
          assertThat(integrity.verify(project).valid()).isFalse();
        });
    assertThat(integrity.verify(project).valid()).isTrue();
  }

  @Test
  void auditVerificationRequiresProjectOwnerOrAdminEvenWhenArchived() {
    registerTestUser();
    String owner = sessionCookie;
    long project = project();
    String path = "/api/projects/" + project + "/audit/verify";
    assertThat(request(HttpMethod.GET, path, null).getBody()).containsEntry("valid", true);
    for (String role : new String[] {"Viewer", "Member", "Admin"}) {
      registerTestUser();
      String member = sessionCookie;
      sessionCookie = owner;
      assertThat(
              request(
                      HttpMethod.POST,
                      "/api/projects/" + project + "/members",
                      Map.of("email", testEmail, "role", role))
                  .getStatusCode())
          .isEqualTo(HttpStatus.OK);
      sessionCookie = member;
      assertThat(request(HttpMethod.GET, path, null).getStatusCode())
          .isEqualTo(role.equals("Admin") ? HttpStatus.OK : HttpStatus.FORBIDDEN);
    }
    registerTestUser();
    assertThat(request(HttpMethod.GET, path, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    sessionCookie = owner;
    assertThat(
            request(
                    HttpMethod.PUT,
                    "/api/projects/" + project + "/archive",
                    Map.of("archived", true))
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(request(HttpMethod.GET, path, null).getBody()).containsEntry("valid", true);
  }

  private long project() {
    return id(
        request(
            HttpMethod.POST,
            "/api/projects",
            Map.of("name", "Audit integrity " + UUID.randomUUID())));
  }
}

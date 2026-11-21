package com.sprintflowinsight.workspace.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprintflowinsight.support.HttpFlowTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;

class OrganizationHttpIntegrationTest extends HttpFlowTest {
  private static final String ROOT = "/api/organizations";

  @Test
  void newAccountsHaveAPersonalOrganizationAndProjectNamesAreScoped() {
    registerTestUser();
    var personal = list(ROOT);
    assertThat(personal).hasSize(1);
    assertThat(personal.getFirst()).containsEntry("personal", true).containsEntry("role", "Owner");

    String first = createOrganization();
    String second = createOrganization();
    var project = Map.<String, Object>of("name", "Organization project");
    assertThat(request(HttpMethod.POST, first + "/projects", project).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(request(HttpMethod.POST, second + "/projects", project).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(request(HttpMethod.POST, first + "/projects", project).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(list(first + "/projects")).hasSize(1);
    assertThat(list(second + "/projects")).hasSize(1);
    assertThat(
            request(
                    HttpMethod.POST,
                    ROOT,
                    Map.of("slug", first.substring(ROOT.length() + 1), "name", "Another"))
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void invitationsBindTheRecipientAndRolesGateOrganizationActions() {
    registerTestUser();
    String owner = sessionCookie;
    String org = createOrganization();
    String unrelated = createOrganization();

    registerTestUser();
    String teammate = sessionCookie;
    String email = testEmail;
    long user = userRepository.findByEmail(email).orElseThrow().getId();
    assertThat(request(HttpMethod.GET, org + "/members", null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    sessionCookie = owner;
    var issued =
        request(HttpMethod.POST, org + "/invites", Map.of("email", email, "role", "Viewer"));
    assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(issued.getHeaders().getCacheControl()).contains("no-store");
    String token = (String) issued.getBody().get("token");
    assertThat(token).hasSize(43);
    assertThat(list(org + "/invites").getFirst()).doesNotContainKeys("token", "tokenDigest");

    registerTestUser();
    assertThat(
            request(HttpMethod.POST, org + "/invites/accept", Map.of("token", token))
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    sessionCookie = teammate;
    assertThat(
            request(HttpMethod.POST, unrelated + "/invites/accept", Map.of("token", token))
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            request(HttpMethod.POST, org + "/invites/accept", Map.of("token", token))
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(
            request(HttpMethod.POST, org + "/invites/accept", Map.of("token", token))
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(list(org + "/members"))
        .anySatisfy(
            row -> assertThat(row).containsEntry("email", email).containsEntry("role", "Viewer"));
    assertThat(
            request(HttpMethod.POST, org + "/projects", Map.of("name", "Viewer project"))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            request(HttpMethod.POST, org + "/teams", Map.of("name", "Viewer team")).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    sessionCookie = owner;
    assertThat(
            request(HttpMethod.PUT, org + "/members/" + user, Map.of("role", "Member"))
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    sessionCookie = teammate;
    assertThat(
            request(HttpMethod.POST, org + "/projects", Map.of("name", "Member project"))
                .getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(request(HttpMethod.GET, org + "/invites", null).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(
            request(HttpMethod.POST, org + "/teams", Map.of("name", "Member team")).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    sessionCookie = owner;
    assertThat(
            request(HttpMethod.PUT, org + "/members/" + user, Map.of("role", "Admin"))
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    sessionCookie = teammate;
    long team = id(request(HttpMethod.POST, org + "/teams", Map.of("name", "Delivery")));
    assertThat(
            request(HttpMethod.PUT, org + "/teams/" + team + "/members/" + user, null)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(list(org + "/teams/" + team + "/members")).hasSize(1);
    assertThat(
            request(
                    HttpMethod.POST,
                    org + "/invites",
                    Map.of("email", "new-admin@example.com", "role", "Admin"))
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    sessionCookie = owner;
    assertThat(
            request(HttpMethod.PUT, unrelated + "/teams/" + team, Map.of("name", "Wrong tenant"))
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(request(HttpMethod.DELETE, org + "/members/" + user, null).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(list(org + "/teams/" + team + "/members")).isEmpty();
    sessionCookie = teammate;
    assertThat(request(HttpMethod.GET, org + "/members", null).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void templatesReuseProjectInitializationAndCannotBeAppliedAcrossOrganizations() {
    registerTestUser();
    String first = createOrganization();
    String second = createOrganization();
    long id =
        id(
            request(
                HttpMethod.POST,
                first + "/templates",
                Map.of(
                    "name",
                    "Delivery pattern",
                    "kind",
                    "Scrum",
                    "description",
                    "Starter description")));
    assertThat(list(first + "/templates")).hasSize(1);
    assertThat(list(second + "/templates")).isEmpty();
    var project = Map.<String, Object>of("name", "Sprint team");
    assertThat(
            request(HttpMethod.POST, second + "/templates/" + id + "/projects", project)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    var created = request(HttpMethod.POST, first + "/templates/" + id + "/projects", project);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getBody()).containsEntry("description", "Starter description");
    assertThat(list("/api/tasks?projectId=" + id(created))).hasSize(3);
  }

  @Test
  void sharedOrganizationOwnershipTransfersBeforeAnAccountCanBeDeleted() {
    registerTestUser();
    String owner = sessionCookie;
    String ownerEmail = testEmail;
    String organization = createOrganization();
    registerTestUser();
    String recipient = sessionCookie;
    String recipientEmail = testEmail;
    long newOwner = userRepository.findByEmail(recipientEmail).orElseThrow().getId();
    sessionCookie = owner;
    var invite =
        request(
            HttpMethod.POST,
            organization + "/invites",
            Map.of("email", recipientEmail, "role", "Member"));
    sessionCookie = recipient;
    assertThat(
            request(
                    HttpMethod.POST,
                    organization + "/invites/accept",
                    Map.of("token", invite.getBody().get("token")))
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);

    sessionCookie = owner;
    var account = Map.<String, Object>of("email", ownerEmail, "currentPassword", "secure-password");
    assertThat(request(HttpMethod.DELETE, "/api/auth/account", account).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            request(HttpMethod.PUT, organization + "/ownership", Map.of("userId", newOwner))
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(request(HttpMethod.DELETE, "/api/auth/account", account).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    sessionCookie = recipient;
    assertThat(list(organization + "/members"))
        .singleElement()
        .satisfies(
            member ->
                assertThat(member)
                    .containsEntry("email", recipientEmail)
                    .containsEntry("role", "Owner"));
  }

  @Test
  void postgresDeniesAnUnfilteredQueryAcrossOrganizationScopes() {
    registerTestUser();
    String first = createOrganization();
    String second = createOrganization();
    long firstTeam = id(request(HttpMethod.POST, first + "/teams", Map.of("name", "First")));
    id(request(HttpMethod.POST, second + "/teams", Map.of("name", "Second")));
    long firstOrg = organizationId(first);
    long secondOrg = organizationId(second);
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              status.setRollbackOnly();
              // Testcontainers' initial database account is a superuser. Exercise the same
              // policies under a regular database role, including when it owns the tables.
              if (Boolean.TRUE.equals(
                  jdbc.queryForObject(
                      "select rolsuper or rolbypassrls from pg_roles where rolname=current_user",
                      Boolean.class))) {
                String role = "rls_test_" + UUID.randomUUID().toString().replace("-", "");
                jdbc.execute("create role " + role + " nologin");
                jdbc.execute("grant select,insert on team to " + role);
                jdbc.execute("grant usage on sequence team_id_seq to " + role);
                jdbc.execute("set local role " + role);
              }
              assertThat(jdbc.queryForList("select id from team", Long.class)).isEmpty();
              jdbc.queryForObject(
                  "select set_config('app.org_id', ?, true)",
                  String.class,
                  Long.toString(firstOrg));
              assertThat(jdbc.queryForList("select id from team", Long.class))
                  .containsExactly(firstTeam);
              assertThatThrownBy(
                      () ->
                          jdbc.update(
                              "insert into team(organization_id,name) values(?,?)",
                              secondOrg,
                              "Blocked by RLS"))
                  .isInstanceOf(DataAccessException.class)
                  .rootCause()
                  .isInstanceOfSatisfying(
                      java.sql.SQLException.class,
                      denial -> {
                        assertThat(denial.getSQLState()).isEqualTo("42501");
                        assertThat(denial.getMessage()).contains("row-level security");
                      });
            });
  }

  private String createOrganization() {
    String slug = "org-" + UUID.randomUUID();
    var response =
        request(HttpMethod.POST, ROOT, Map.of("name", "Team organization", "slug", slug));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return ROOT + "/" + slug;
  }

  private long organizationId(String path) {
    return jdbc.queryForObject(
        "select id from organization where slug=?", Long.class, path.substring(ROOT.length() + 1));
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> list(String path) {
    var response =
        rest.exchange(path, HttpMethod.GET, new HttpEntity<>(cookieHeaders()), List.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response.getBody();
  }
}

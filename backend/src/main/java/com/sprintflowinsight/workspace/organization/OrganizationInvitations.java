package com.sprintflowinsight.workspace.organization;

import com.sprintflowinsight.common.SecureTokens;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.organization.OrgAccessService.Need;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import jakarta.servlet.http.HttpSession;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrganizationInvitations {
  record Pending(long id, String email, String role, OffsetDateTime expiresAt) {}

  record Issued(Pending invitation, String token) {
    @Override
    public String toString() {
      return "Issued[invitationId=" + invitation.id() + ", token=redacted]";
    }
  }

  private final JdbcTemplate jdbc;
  private final OrgAccessService access;
  private final ProjectAccessService account;

  OrganizationInvitations(
      JdbcTemplate jdbc, OrgAccessService access, ProjectAccessService account) {
    this.jdbc = jdbc;
    this.access = access;
    this.account = account;
  }

  @Transactional(readOnly = true)
  public List<Pending> list(String slug, HttpSession session) {
    var scope = access.require(slug, session, Need.MANAGE);
    access.useDataRole();
    return jdbc.query(
        """
        select id,email,role,expires_at from organization_invite
        where organization_id=? and consumed_at is null and expires_at>clock_timestamp()
        order by id desc limit 100
        """,
        (row, index) ->
            new Pending(
                row.getLong(1),
                row.getString(2),
                row.getString(3),
                row.getObject(4, OffsetDateTime.class)),
        scope.organizationId());
  }

  @Transactional
  public Issued issue(String slug, String email, String role, HttpSession session) {
    var scope = access.require(slug, session, Need.MANAGE);
    access.useDataRole();
    OrganizationService.requireAssignable(scope, role);
    String recipient = normalize(email);
    if (Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            select exists(select 1 from organization_member m join users u on u.id=m.user_id
                          where m.organization_id=? and lower(u.email)=?)
            """,
            Boolean.class,
            scope.organizationId(),
            recipient)))
      throw ApiException.conflict("This account is already an organization member.");
    jdbc.update(
        """
        update organization_invite set consumed_at=clock_timestamp()
        where organization_id=? and email=? and consumed_at is null
        """,
        scope.organizationId(),
        recipient);
    String token = SecureTokens.token();
    Pending invitation =
        jdbc.queryForObject(
            """
insert into organization_invite(organization_id, token_digest, email, role, created_by, expires_at)
values(?,?,?,?,?,clock_timestamp()+interval '7 days') returning id,expires_at
""",
            (row, index) ->
                new Pending(
                    row.getLong(1), recipient, role, row.getObject(2, OffsetDateTime.class)),
            scope.organizationId(),
            digest(token),
            recipient,
            role,
            scope.userId());
    return new Issued(invitation, token);
  }

  @Transactional
  public void revoke(String slug, long id, HttpSession session) {
    var scope = access.require(slug, session, Need.MANAGE);
    access.useDataRole();
    if (jdbc.update(
            """
            update organization_invite set consumed_at=clock_timestamp()
            where id=? and organization_id=? and consumed_at is null
            """,
            id,
            scope.organizationId())
        == 0) throw unavailable();
  }

  @Transactional
  public void accept(String slug, String token, HttpSession session) {
    long user = account.userId(session);
    String email = normalize(account.email(session));
    long organization =
        jdbc
            .queryForList(
                "select id from organization where slug=? for no key update", Long.class, slug)
            .stream()
            .findFirst()
            .orElseThrow(OrganizationInvitations::unavailable);
    access.bind(organization);
    String role =
        access.readOrClaimScopedData(
            () ->
                jdbc
                    .queryForList(
                        """
update organization_invite set consumed_at=clock_timestamp()
where organization_id=? and token_digest=? and email=? and consumed_at is null
  and expires_at>clock_timestamp()
returning role
""",
                        String.class,
                        organization,
                        digest(token),
                        email)
                    .stream()
                    .findFirst()
                    .orElseThrow(OrganizationInvitations::unavailable));
    jdbc.update(
        """
        insert into organization_member(organization_id,user_id,role) values(?,?,?)
        on conflict (organization_id,user_id) do nothing
        """,
        organization,
        user,
        role);
  }

  private static ApiException unavailable() {
    return ApiException.notFound("This invitation is unavailable.");
  }

  private static byte[] digest(String token) {
    return HexFormat.of().parseHex(SecureTokens.sha256(token));
  }

  private static String normalize(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }
}

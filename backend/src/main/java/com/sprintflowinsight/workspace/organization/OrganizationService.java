package com.sprintflowinsight.workspace.organization;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.organization.OrgAccessService.Context;
import com.sprintflowinsight.workspace.organization.OrgAccessService.Need;
import com.sprintflowinsight.workspace.project.Project;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectFactory;
import com.sprintflowinsight.workspace.project.ProjectRepository;
import com.sprintflowinsight.workspace.project.ProjectRequest;
import com.sprintflowinsight.workspace.project.ProjectRole;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrganizationService {
  record Organization(long id, String slug, String name, String role, boolean personal) {}

  record Member(long userId, String email, String name, String role) {}

  private final JdbcTemplate jdbc;
  private final OrgAccessService access;
  private final ProjectAccessService account;
  private final ProjectRepository projects;
  private final ProjectFactory factory;

  OrganizationService(
      JdbcTemplate jdbc,
      OrgAccessService access,
      ProjectAccessService account,
      ProjectRepository projects,
      ProjectFactory factory) {
    this.jdbc = jdbc;
    this.access = access;
    this.account = account;
    this.projects = projects;
    this.factory = factory;
  }

  @Transactional(readOnly = true)
  public List<Organization> list(HttpSession session) {
    return jdbc.query(
        """
        select o.id,o.slug,o.name,m.role,o.personal_for_user_id is not null
        from organization_member m join organization o on o.id=m.organization_id
        where m.user_id=? order by o.personal_for_user_id is null,lower(o.name),o.id
        """,
        (row, index) ->
            new Organization(
                row.getLong(1),
                row.getString(2),
                row.getString(3),
                row.getString(4),
                row.getBoolean(5)),
        account.userId(session));
  }

  @Transactional
  public Organization create(String slug, String name, HttpSession session) {
    if (slug.startsWith("personal-") || slug.equals("legacy-unassigned"))
      throw ApiException.badRequest("Choose a different organization slug.");
    long user = account.userId(session);
    Long id =
        jdbc
            .queryForList(
                """
insert into organization(slug,name) values(?,?) on conflict(slug) do nothing returning id
""",
                Long.class,
                slug,
                name.strip())
            .stream()
            .findFirst()
            .orElseThrow(() -> ApiException.conflict("This organization slug is already in use."));
    jdbc.update(
        "insert into organization_member(organization_id,user_id,role) values(?,?,'Owner')",
        id,
        user);
    return new Organization(id, slug, name.strip(), ProjectRole.OWNER.label(), false);
  }

  @Transactional(readOnly = true)
  public List<Project> listProjects(String slug, HttpSession session) {
    var context = access.require(slug, session, Need.READ);
    return projects.findVisibleInOrganization(context.organizationId(), context.userId());
  }

  public Project createProject(String slug, ProjectRequest request, HttpSession session) {
    String key = request.normalizedKey();
    String email = account.email(session);
    return factory.inTransactionRetryingDerivedKey(
        key,
        () -> {
          var context = access.require(slug, session, Need.WRITE);
          return factory.createInOrganization(
              context.organizationId(),
              request.trimmedName(),
              request.description(),
              email,
              key,
              null);
        });
  }

  @Transactional(readOnly = true)
  public List<Member> members(String slug, HttpSession session) {
    var context = access.require(slug, session, Need.READ);
    return jdbc.query(
        """
        select u.id,u.email,u.display_name,m.role
        from organization_member m join users u on u.id=m.user_id
        where m.organization_id=? order by lower(u.email),u.id
        """,
        (row, index) ->
            new Member(row.getLong(1), row.getString(2), row.getString(3), row.getString(4)),
        context.organizationId());
  }

  @Transactional
  public void changeRole(String slug, long userId, String role, HttpSession session) {
    var context = access.require(slug, session, Need.MANAGE);
    requireAssignable(context, role);
    requireManageTarget(context, userId);
    jdbc.update(
        "update organization_member set role=? where organization_id=? and user_id=?",
        role,
        context.organizationId(),
        userId);
  }

  @Transactional
  public void removeMember(String slug, long userId, HttpSession session) {
    var context = access.require(slug, session, Need.MANAGE);
    requireManageTarget(context, userId);
    jdbc.update(
        "delete from organization_member where organization_id=? and user_id=?",
        context.organizationId(),
        userId);
  }

  @Transactional
  public void transferOwnership(String slug, long userId, HttpSession session) {
    var context = access.require(slug, session, Need.MANAGE);
    if (context.role() != ProjectRole.OWNER)
      throw ApiException.forbidden("Only the organization owner can transfer ownership.");
    if (userId == context.userId()) return;
    Boolean personal =
        jdbc.queryForObject(
            "select personal_for_user_id is not null from organization where id=?",
            Boolean.class,
            context.organizationId());
    if (Boolean.TRUE.equals(personal))
      throw ApiException.conflict("A personal organization's ownership cannot be transferred.");
    if (role(context.organizationId(), userId) == ProjectRole.VIEWER)
      throw ApiException.badRequest("Choose a member or admin as the new organization owner.");
    jdbc.update(
        "update organization_member set role='Admin' where organization_id=? and user_id=?",
        context.organizationId(),
        context.userId());
    jdbc.update(
        "update organization_member set role='Owner' where organization_id=? and user_id=?",
        context.organizationId(),
        userId);
  }

  static void requireAssignable(Context context, String role) {
    if ("Admin".equals(role) && context.role() != ProjectRole.OWNER)
      throw ApiException.forbidden("Only the organization owner can appoint an admin.");
  }

  private void requireManageTarget(Context context, long target) {
    ProjectRole role = role(context.organizationId(), target);
    if (role == ProjectRole.OWNER
        || (role == ProjectRole.ADMIN && context.role() != ProjectRole.OWNER))
      throw ApiException.forbidden("You cannot change that organization member.");
  }

  private ProjectRole role(long id, long target) {
    return jdbc
        .queryForList(
            "select role from organization_member where organization_id=? and user_id=?",
            String.class,
            id,
            target)
        .stream()
        .map(ProjectRole::fromLabel)
        .findFirst()
        .orElseThrow(() -> ApiException.notFound("Organization member not found."));
  }
}

package com.sprintflowinsight.workspace.organization;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.organization.OrgAccessService.Need;
import com.sprintflowinsight.workspace.project.Project;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectFactory;
import com.sprintflowinsight.workspace.project.ProjectRequest;
import com.sprintflowinsight.workspace.project.ProjectTemplateService;
import com.sprintflowinsight.workspace.project.TemplateRequest;
import jakarta.servlet.http.HttpSession;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrganizationTemplates {
  record Template(long id, String name, String kind, String description) {}

  private final JdbcTemplate jdbc;
  private final OrgAccessService access;
  private final ProjectAccessService account;
  private final ProjectFactory factory;
  private final ProjectTemplateService starter;

  OrganizationTemplates(
      JdbcTemplate jdbc,
      OrgAccessService access,
      ProjectAccessService account,
      ProjectFactory factory,
      ProjectTemplateService starter) {
    this.jdbc = jdbc;
    this.access = access;
    this.account = account;
    this.factory = factory;
    this.starter = starter;
  }

  @Transactional(readOnly = true)
  public List<Template> list(String slug, HttpSession session) {
    long organization = access.require(slug, session, Need.READ).organizationId();
    access.useDataRole();
    return jdbc.query(
        """
        select id,name,template_kind,description from organization_template
        where organization_id=? order by lower(name),id
        """,
        OrganizationTemplates::template,
        organization);
  }

  @Transactional
  public Template create(
      String slug, String name, String kind, String description, HttpSession session) {
    long organization = access.require(slug, session, Need.MANAGE).organizationId();
    access.useDataRole();
    return jdbc.queryForObject(
        """
        insert into organization_template(organization_id,name,template_kind,description)
        values(?,?,?,?) returning id,name,template_kind,description
        """,
        OrganizationTemplates::template,
        organization,
        name.strip(),
        kind,
        description);
  }

  @Transactional
  public void delete(String slug, long id, HttpSession session) {
    long organization = access.require(slug, session, Need.MANAGE).organizationId();
    access.useDataRole();
    if (jdbc.update(
            "delete from organization_template where organization_id=? and id=?", organization, id)
        == 0) throw notFound();
  }

  public Project instantiate(
      String slug, long templateId, ProjectRequest request, String language, HttpSession session) {
    String email = account.email(session);
    return factory.inTransactionRetryingDerivedKey(
        request.normalizedKey(),
        () -> {
          long organization = access.require(slug, session, Need.WRITE).organizationId();
          var template =
              access.readOrClaimScopedData(
                  () ->
                      jdbc
                          .query(
                              """
                              select id,name,template_kind,description from organization_template
                              where organization_id=? and id=?
                              """,
                              OrganizationTemplates::template,
                              organization,
                              templateId)
                          .stream()
                          .findFirst()
                          .orElseThrow(OrganizationTemplates::notFound));
          return starter.createInOrganization(
              new TemplateRequest(
                  request.trimmedName(),
                  request.description() == null ? template.description() : request.description(),
                  template.kind(),
                  language,
                  request.key()),
              email,
              organization);
        });
  }

  private static Template template(ResultSet row, int index) throws SQLException {
    return new Template(row.getLong(1), row.getString(2), row.getString(3), row.getString(4));
  }

  private static ApiException notFound() {
    return ApiException.notFound("Organization template not found.");
  }
}

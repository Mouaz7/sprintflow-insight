package com.sprintflowinsight.workspace.organization;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.organization.OrgAccessService.Need;
import com.sprintflowinsight.workspace.organization.OrganizationService.Member;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrganizationTeams {
  record Team(long id, String name, int memberCount) {}

  private final JdbcTemplate jdbc;
  private final OrgAccessService access;

  OrganizationTeams(JdbcTemplate jdbc, OrgAccessService access) {
    this.jdbc = jdbc;
    this.access = access;
  }

  @Transactional(readOnly = true)
  public List<Team> list(String slug, HttpSession session) {
    var scope = access.require(slug, session, Need.READ);
    access.useDataRole();
    return jdbc.query(
        """
        select t.id,t.name,count(tm.user_id) from team t
        left join team_member tm on tm.team_id=t.id and tm.organization_id=t.organization_id
        where t.organization_id=? group by t.id order by lower(t.name),t.id
        """,
        (row, index) -> new Team(row.getLong(1), row.getString(2), row.getInt(3)),
        scope.organizationId());
  }

  @Transactional
  public Team create(String slug, String name, HttpSession session) {
    long organization = access.require(slug, session, Need.MANAGE).organizationId();
    access.useDataRole();
    long id =
        jdbc.queryForObject(
            "insert into team(organization_id,name) values(?,?) returning id",
            Long.class,
            organization,
            name.strip());
    return new Team(id, name.strip(), 0);
  }

  @Transactional
  public void rename(String slug, long id, String name, HttpSession session) {
    long organization = access.require(slug, session, Need.MANAGE).organizationId();
    access.useDataRole();
    if (jdbc.update(
            "update team set name=? where id=? and organization_id=?",
            name.strip(),
            id,
            organization)
        == 0) throw missingTeam();
  }

  @Transactional
  public void delete(String slug, long id, HttpSession session) {
    long organization = access.require(slug, session, Need.MANAGE).organizationId();
    access.useDataRole();
    if (jdbc.update("delete from team where id=? and organization_id=?", id, organization) == 0)
      throw missingTeam();
  }

  @Transactional(readOnly = true)
  public List<Member> members(String slug, long id, HttpSession session) {
    long organization = access.require(slug, session, Need.READ).organizationId();
    access.useDataRole();
    requireTeam(organization, id);
    return jdbc.query(
        """
        select u.id,u.email,u.display_name,m.role
        from team_member tm join users u on u.id=tm.user_id
        join organization_member m on m.user_id=tm.user_id and m.organization_id=tm.organization_id
        where tm.team_id=? and tm.organization_id=? order by lower(u.email),u.id
        """,
        (row, index) ->
            new Member(row.getLong(1), row.getString(2), row.getString(3), row.getString(4)),
        id,
        organization);
  }

  @Transactional
  public void addMember(String slug, long id, long user, HttpSession session) {
    long organization = access.require(slug, session, Need.MANAGE).organizationId();
    access.useDataRole();
    requireTeam(organization, id);
    if (!Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from organization_member where organization_id=? and"
                + " user_id=?)",
            Boolean.class,
            organization,
            user))) throw ApiException.notFound("Organization member not found.");
    jdbc.update(
        """
        insert into team_member(team_id,organization_id,user_id) values(?,?,?)
          on conflict (team_id,user_id) do nothing
        """,
        id,
        organization,
        user);
  }

  @Transactional
  public void removeMember(String slug, long id, long user, HttpSession session) {
    long organization = access.require(slug, session, Need.MANAGE).organizationId();
    access.useDataRole();
    requireTeam(organization, id);
    jdbc.update(
        "delete from team_member where team_id=? and organization_id=? and user_id=?",
        id,
        organization,
        user);
  }

  private void requireTeam(long organization, long id) {
    if (!Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from team where organization_id=? and id=?)",
            Boolean.class,
            organization,
            id))) throw missingTeam();
  }

  private static ApiException missingTeam() {
    return ApiException.notFound("Team not found.");
  }
}

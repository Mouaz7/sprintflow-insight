package com.sprintflowinsight.workspace.organization;

import com.sprintflowinsight.auth.AccountDeleting;
import com.sprintflowinsight.common.error.ApiException;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class OrganizationAccountListener {
  private final JdbcTemplate jdbc;

  OrganizationAccountListener(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  record Owned(long id, boolean personal) {}

  @EventListener
  @Transactional(propagation = Propagation.MANDATORY)
  public void deleting(AccountDeleting event) {
    var owned =
        jdbc.query(
            """
            select o.id,o.personal_for_user_id is not null
            from organization_member m join organization o on o.id=m.organization_id
            where m.user_id=? and m.role='Owner' order by o.id for update of o
            """,
            (row, index) -> new Owned(row.getLong(1), row.getBoolean(2)),
            event.userId());
    for (var organization : owned) {
      if (Boolean.TRUE.equals(
          jdbc.queryForObject(
              """
select exists(select 1 from organization_member where organization_id=? and user_id<>?)
""",
              Boolean.class,
              organization.id(),
              event.userId()))) {
        throw ApiException.conflict(
            organization.personal()
                ? "Remove members from your personal organization before deleting this account."
                : "Transfer ownership of your shared organizations before deleting this account.");
      }
      jdbc.update(
          """
          delete from organization o where o.id=?
          and not exists (select 1 from projects p where p.organization_id=o.id)
          """,
          organization.id());
    }
  }
}

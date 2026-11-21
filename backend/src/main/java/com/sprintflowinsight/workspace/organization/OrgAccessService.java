package com.sprintflowinsight.workspace.organization;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectRole;
import jakarta.servlet.http.HttpSession;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class OrgAccessService {
  public enum Need {
    READ,
    WRITE,
    MANAGE
  }

  public record Context(long organizationId, long userId, ProjectRole role) {}

  private final JdbcTemplate jdbc;
  private final ProjectAccessService account;

  OrgAccessService(JdbcTemplate jdbc, ProjectAccessService account) {
    this.jdbc = jdbc;
    this.account = account;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Context require(String slug, HttpSession session, Need need) {
    long userId = account.userId(session);
    String sql =
        """
        select o.id, m.role from organization o
        join organization_member m on m.organization_id=o.id
        where o.slug=? and m.user_id=?
        """;
    boolean lock =
        need != Need.READ && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    var matching =
        jdbc.query(
            sql + (lock ? " for no key update of o, m" : ""),
            (row, index) ->
                new Context(row.getLong(1), userId, ProjectRole.fromLabel(row.getString(2))),
            slug,
            userId);
    var context =
        matching.stream()
            .findFirst()
            .orElseThrow(() -> ApiException.notFound("Organization not found."));
    if (need == Need.WRITE && !context.role().canWrite()
        || need == Need.MANAGE && !context.role().canManage())
      throw ApiException.forbidden("Your organization role does not permit this change.");
    bind(context.organizationId());
    return context;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public long personal(long userId) {
    return jdbc.queryForObject(
        "select id from organization where personal_for_user_id=?", Long.class, userId);
  }

  void bind(long organizationId) {
    // set_config's third argument makes this equivalent to SET LOCAL on the transaction connection.
    jdbc.queryForObject(
        "select set_config('app.org_id', ?, true)", String.class, Long.toString(organizationId));
  }

  /** Switch only for data protected by RLS when the connection would otherwise bypass it. */
  String useDataRole() {
    String privileged =
        jdbc
            .queryForList(
                "select current_user from pg_roles where rolname=current_user and (rolsuper or"
                    + " rolbypassrls)",
                String.class)
            .stream()
            .findFirst()
            .orElse(null);
    if (privileged != null) jdbc.execute("set local role sprintflow_organization_scope");
    return privileged;
  }

  /** Select scoped data before continuing with existing project or membership writes. */
  <T> T readOrClaimScopedData(Supplier<T> query) {
    String before = useDataRole();
    T found = query.get();
    if (before != null)
      jdbc.queryForObject("select set_config('role', ?, true)", String.class, before);
    return found;
  }
}

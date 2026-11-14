package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * At most one active, non-trashed sprint per project, enforced by a partial unique index.
 *
 * <p>Kept as a Java migration (not rewritten as SQL) on purpose: Java migrations carry no
 * checksum, so databases that already applied V8 validate unchanged and need no {@code flyway
 * repair}. The H2 branch was removed when the test suite moved to PostgreSQL (ADR 0005).
 */
public class V8__single_active_sprint extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    var connection = context.getConnection();
    if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()))
      throw new java.sql.SQLException("SprintFlow supports PostgreSQL only.");
    try (var statement = connection.createStatement()) {
      try (var conflicts =
          statement.executeQuery(
              "select project_id from sprint where status = 'Active' and trashed_at is null group"
                  + " by project_id having count(*) > 1")) {
        if (conflicts.next())
          throw new java.sql.SQLException(
              "Multiple active sprints exist. Resolve these conflicts before migrating; no sprint"
                  + " status was changed.");
      }
      statement.execute(
          "create unique index sprint_one_active_project_idx on sprint(project_id) where status ="
              + " 'Active' and trashed_at is null");
    }
  }
}

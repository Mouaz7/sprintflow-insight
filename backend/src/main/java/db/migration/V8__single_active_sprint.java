package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V8__single_active_sprint extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    var connection = context.getConnection();
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
      if (connection.getMetaData().getDatabaseProductName().equals("PostgreSQL"))
        statement.execute(
            "create unique index sprint_one_active_project_idx on sprint(project_id) where status ="
                + " 'Active' and trashed_at is null");
      else if (connection.getMetaData().getDatabaseProductName().equals("H2")) {
        statement.execute(
            "alter table sprint add column active_project_id bigint generated always as (case when"
                + " status = 'Active' and trashed_at is null then project_id else null end)");
        statement.execute(
            "create unique index sprint_one_active_project_idx on sprint(active_project_id)");
      } else
        throw new java.sql.SQLException(
            "Single active sprint migration supports PostgreSQL and H2 only.");
    }
  }
}

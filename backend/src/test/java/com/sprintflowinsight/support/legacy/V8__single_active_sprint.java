package com.sprintflowinsight.support.legacy;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Frozen PostgreSQL path of the Java V8 shipped before A2; only for upgrade tests. */
public class V8__single_active_sprint extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    try (var statement = context.getConnection().createStatement()) {
      try (var conflicts =
          statement.executeQuery(
              "select project_id from sprint where status = 'Active' and trashed_at is null group"
                  + " by project_id having count(*) > 1")) {
        if (conflicts.next()) throw new java.sql.SQLException("Multiple active sprints exist.");
      }
      statement.execute(
          "create unique index sprint_one_active_project_idx on sprint(project_id) where status ="
              + " 'Active' and trashed_at is null");
    }
  }
}

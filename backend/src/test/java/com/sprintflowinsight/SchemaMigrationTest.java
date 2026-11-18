package com.sprintflowinsight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprintflowinsight.support.MigrationFixture;
import java.nio.file.Path;
import java.sql.Statement;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Upgrade safety: every migration must apply on top of data written by the previous release, and
 * rules enforced by the schema must hold on the real engine. Each test uses its own schema.
 */
class SchemaMigrationTest {
  @Test
  void v20RetainsExistingEstimateValuesAndInitializesTheirRevision() throws Exception {
    try (var db = new MigrationFixture()) {
      db.migrateTo("19");
      try (var sql = db.connection().createStatement()) {
        sql.executeUpdate(
            "insert into projects(id,name,owner_email,version,key) values"
                + " (1,'P','owner@example.com',3,'PP')");
        sql.executeUpdate(
            "insert into task(id,project_id,number,title,status,is_blocked,story_points,version)"
                + " values (1,1,1,'Existing','To Do',false,3,7)");
        sql.executeUpdate(
            "insert into"
                + " task_estimate(task_id,project_id,optimistic_days,most_likely_days,pessimistic_days,updated_at)"
                + " values (1,1,1.25,2.50,5.75,now())");
        assertThat(db.migrateTo("20").migrationsExecuted).isEqualTo(1);
        db.migrateTo(null);
        db.flyway().validate();
        try (var rows =
            sql.executeQuery(
                "select optimistic_days,most_likely_days,pessimistic_days,version,active from"
                    + " task_estimate where task_id=1")) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("1.25");
          assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("2.50");
          assertThat(rows.getBigDecimal(3)).isEqualByComparingTo("5.75");
          assertThat(rows.getLong(4)).isZero();
          assertThat(rows.getBoolean(5)).isTrue();
        }
      }
    }
  }

  @Test
  void v21AddsScopedWipSettingsWithoutRewritingWorkflowHistory() throws Exception {
    try (var db = new MigrationFixture()) {
      db.migrateTo("20");
      try (var sql = db.connection().createStatement()) {
        sql.executeUpdate(
            "insert into projects(id,name,owner_email,version,key) values"
                + " (1,'Flow','owner@example.com',4,'FF')");
        sql.executeUpdate(
            "insert into task(id,project_id,number,title,status,is_blocked,story_points,version)"
                + " values (1,1,1,'Existing','In Progress',false,3,7)");
        sql.executeUpdate(
            "insert into"
                + " task_status_history(task_id,project_id,status,changed_at,actor_email,story_points)"
                + " values (1,1,'In Progress','2025-01-01T12:00:00Z','owner@example.com',3)");
        assertThat(db.migrateTo("21").migrationsExecuted).isEqualTo(1);
        db.migrateTo(null);
        db.flyway().validate();
        try (var rows =
            sql.executeQuery(
                "select status,story_points from task_status_history where task_id=1")) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getString(1)).isEqualTo("In Progress");
          assertThat(rows.getInt(2)).isEqualTo(3);
        }
        sql.executeUpdate(
            "insert into projects(id,name,owner_email,version,key) values (2,'Limits"
                + " only','owner@example.com',0,'WW')");
        sql.executeUpdate("insert into project_wip_settings(project_id,version) values (2,1)");
        sql.executeUpdate(
            "insert into project_wip_limit(project_id,status,wip_limit) values (2,'In"
                + " Progress',2)");
        try (var rows =
            sql.executeQuery(
                "select count(*) from pg_indexes where schemaname=current_schema() and"
                    + " indexname='history_project_time_idx'")) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getInt(1)).isEqualTo(1);
        }
        sql.executeUpdate("delete from projects where id=2");
        for (String table : java.util.List.of("project_wip_limit", "project_wip_settings")) {
          try (var rows = sql.executeQuery("select count(*) from " + table)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getLong(1)).isZero();
          }
        }
      }
    }
  }

  /** Temporary migration directory for the previous release (V12, before A1). */
  @TempDir Path previousMigrations;

  @Test
  void upgradingFromThePreviousReleaseKeepsExistingDataUsable() throws Exception {
    try (var db = new MigrationFixture()) {
      db.migratePreviousRelease(previousMigrations);
      try (Statement sql = db.connection().createStatement()) {
        sql.executeUpdate(
            "insert into users(id,email,password,display_name) values"
                + " (1,'owner@example.com','$2a$12$hash','Owner')");
        sql.executeUpdate(
            "insert into projects(id,name,owner_email,version) values"
                + " (101,'Existing','owner@example.com',3)");
        sql.executeUpdate(
            "insert into project_member(id,project_id,email,role) values"
                + " (1,101,'owner@example.com','Owner')");
        sql.executeUpdate(
            "insert into"
                + " sprint(id,name,status,total_story_points,project_id,version,start_date,end_date)"
                + " values (11,'Sprint 1','Active',8,101,2,date '2026-01-05',date '2026-01-19')");
        sql.executeUpdate(
            "insert into"
                + " user_stories(id,title,acceptance_criteria,story_points,priority_id,project_id,sprint_id,version,backlog_position)"
                + " values (201,'Story','Done when done',5,2,101,11,4,0)");
        sql.executeUpdate(
            "insert into"
                + " task(id,title,status,is_blocked,project_id,sprint_id,story_id,story_points,version)"
                + " values (301,'Task','In Progress',false,101,11,201,3,6)");

        // Java V8 has no checksum and a different type: upgrading must require the
        // explicit, one-time operator repair from ADR 0005, never silently rewrite history.
        assertThat(db.flyway().validateWithResult().validationSuccessful).isFalse();
        assertThatThrownBy(() -> db.migrateTo(null)).isInstanceOf(FlywayException.class);
        // Flyway 11 treats a Java-to-SQL type change as missing instead of aligning it.
        // Match the guarded operator step in ADR 0005 before repairing the checksum.
        assertThat(
                sql.executeUpdate(
                    "update flyway_schema_history set type = 'SQL', script ="
                        + " 'V8__single_active_sprint.sql' where version = '8' and type = 'JDBC'"
                        + " and checksum is null and success"))
            .isEqualTo(1);
        var repair = db.flyway().repair();
        assertThat(repair.migrationsAligned).hasSize(1);
        assertThat(repair.migrationsDeleted).isEmpty();
        assertThat(repair.migrationsRemoved).isEmpty();
        var result = db.migrateTo(null);
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isPositive();
        db.flyway().validate();

        try (var rows =
            sql.executeQuery(
                "select p.version, s.status, us.backlog_position, t.version from projects p join"
                    + " sprint s on s.project_id = p.id join user_stories us on us.sprint_id = s.id"
                    + " join task t on t.story_id = us.id")) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getLong(1)).isEqualTo(3);
          assertThat(rows.getString(2)).isEqualTo("Active");
          assertThat(rows.getLong(3)).isZero();
          assertThat(rows.getLong(4)).isEqualTo(6);
        }
        try (var rows = sql.executeQuery("select count(*) from spring_session")) {
          rows.next();
          assertThat(rows.getLong(1)).as("session store created empty").isZero();
        }
        try (var rows =
            sql.executeQuery(
                "select type,checksum from flyway_schema_history where version = '8'")) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getString("type")).isEqualTo("SQL");
          assertThat(rows.getObject("checksum")).isNotNull();
        }
      }
    }
  }

  @Test
  void v15BackfillsKeysTaskNumbersAndMembershipByAccount() throws Exception {
    try (var db = new MigrationFixture()) {
      db.migrateTo("14");
      try (Statement sql = db.connection().createStatement()) {
        sql.executeUpdate(
            "insert into users(id,email,password) values (1,'owner@example.com','h'),"
                + " (2,'member@example.com','h'), (3,'second@example.com','h')");
        sql.executeUpdate(
            "insert into projects(id,name,owner_email,version) values (1,'Sprint"
                + " Flow','owner@example.com',4), (2,'Space Force','owner@example.com',0),"
                + " (3,'2026','Owner@Example.com',0), (4,'Åre Östra','second@example.com',0),"
                + " (5,'Ghost','gone@example.com',0)");
        sql.executeUpdate(
            "insert into project_member(id,project_id,email,role) values"
                // project 1: owner row present, member in two spellings, an orphan address
                + " (1,1,'owner@example.com','Owner'), (2,1,'member@example.com','Viewer'),"
                + " (3,1,'MEMBER@example.com','Admin'), (4,1,'nobody@example.com','Member'),"
                // project 2: owner row missing, a stale Owner row from an old transfer
                + " (5,2,'second@example.com','Owner'),"
                // project 3: owner present with the wrong role
                + " (6,3,'owner@example.com','Member')");
        sql.executeUpdate(
            "insert into task(id,title,status,is_blocked,project_id,story_points,version) values"
                + " (10,'a','To Do',false,1,1,7), (11,'b','To Do',false,2,1,0),"
                + " (12,'c','To Do',false,1,1,0), (13,'d','Done',false,1,1,0)");

        db.migrateTo(null);
        db.flyway().validate();

        try (var rows = sql.executeQuery("select id, key, version from projects order by id")) {
          String[] keys = {"SF", "SFA", "PRJ", "AO", "GHOS"};
          for (String key : keys) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString("key")).isEqualTo(key);
          }
        }
        try (var rows = sql.executeQuery("select version from projects where id = 1")) {
          rows.next();
          assertThat(rows.getLong(1)).as("backfill keeps row versions").isEqualTo(4);
        }
        try (var rows =
            sql.executeQuery("select id, number, version from task order by project_id, id")) {
          long[][] expected = {{10, 1, 7}, {12, 2, 0}, {13, 3, 0}, {11, 1, 0}};
          for (long[] row : expected) {
            assertThat(rows.next()).isTrue();
            assertThat(new long[] {rows.getLong(1), rows.getLong(2), rows.getLong(3)})
                .containsExactly(row);
          }
        }
        try (var rows =
            sql.executeQuery(
                "select project_id, last_number from project_task_counter order by project_id")) {
          long[][] expected = {{1, 3}, {2, 1}, {3, 0}, {4, 0}, {5, 0}};
          for (long[] row : expected) {
            assertThat(rows.next()).isTrue();
            assertThat(new long[] {rows.getLong(1), rows.getLong(2)}).containsExactly(row);
          }
        }
        try (var rows =
            sql.executeQuery(
                "select project_id, user_id, email, role from project_member"
                    + " order by project_id, user_id")) {
          Object[][] expected = {
            {1L, 1L, "owner@example.com", "Owner"},
            {1L, 2L, "member@example.com", "Admin"},
            {2L, 1L, "owner@example.com", "Owner"},
            {2L, 3L, "second@example.com", "Member"},
            {3L, 1L, "owner@example.com", "Owner"},
            {4L, 3L, "second@example.com", "Owner"}
          };
          for (Object[] row : expected) {
            assertThat(rows.next()).isTrue();
            assertThat(
                    new Object[] {
                      rows.getLong(1), rows.getLong(2), rows.getString(3), rows.getString(4)
                    })
                .containsExactly(row);
          }
          assertThat(rows.next()).as("orphan and duplicate rows removed").isFalse();
        }

        // New constraints hold on the real engine.
        assertThatThrownBy(
                () ->
                    sql.executeUpdate(
                        "insert into projects(id,name,owner_email,version,key) values"
                            + " (100,'Lower','o@example.com',0,'sf')"))
            .hasMessageContaining("projects_key_format");
        assertThatThrownBy(
                () ->
                    sql.executeUpdate(
                        "insert into projects(id,name,owner_email,version,key) values"
                            + " (101,'Copy','o@example.com',0,'SF')"))
            .hasMessageContaining("projects_key_unique");
        assertThatThrownBy(
                () ->
                    sql.executeUpdate(
                        "insert into"
                            + " task(id,title,status,is_blocked,project_id,story_points,number)"
                            + " values (100,'dup','To Do',false,1,1,2)"))
            .hasMessageContaining("task_project_number_unique");
        assertThatThrownBy(
                () ->
                    sql.executeUpdate(
                        "insert into project_member(id,project_id,email,role,user_id) values"
                            + " (100,1,'x@example.com','Member',999)"))
            .hasMessageContaining("project_member_user_fk");
        sql.executeUpdate("delete from users where id = 2");
        try (var rows = sql.executeQuery("select count(*) from project_member where user_id = 2")) {
          rows.next();
          assertThat(rows.getLong(1)).as("account removal cascades").isZero();
        }
      }
    }
  }

  @Test
  void addingManualOrderPreservesExistingStoriesAndVersions() throws Exception {
    try (var db = new MigrationFixture()) {
      db.migrateTo("11");
      try (Statement sql = db.connection().createStatement()) {
        sql.executeUpdate(
            "insert into projects(id,name,owner_email,version) values"
                + " (101,'Existing','owner@example.com',0)");
        sql.executeUpdate(
            "insert into"
                + " user_stories(id,title,acceptance_criteria,story_points,priority_id,project_id,version)"
                + " values (201,'Preserved story','Unchanged',5,2,101,7)");
        db.migrateTo(null);
        try (var rows =
            sql.executeQuery(
                "select id,title,story_points,version,backlog_position from user_stories")) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getLong("id")).isEqualTo(201);
          assertThat(rows.getString("title")).isEqualTo("Preserved story");
          assertThat(rows.getInt("story_points")).isEqualTo(5);
          assertThat(rows.getLong("version")).isEqualTo(7);
          assertThat(rows.getLong("backlog_position")).isEqualTo(201);
          assertThat(rows.next()).isFalse();
        }
      }
    }
  }

  @Test
  void singleActiveSprintMigrationRefusesConflictingDataInsteadOfChangingIt() throws Exception {
    try (var db = new MigrationFixture()) {
      db.migrateTo("7");
      try (Statement sql = db.connection().createStatement()) {
        sql.executeUpdate(
            "insert into projects(id,name,owner_email,version) values (1,'P','o@example.com',0)");
        sql.executeUpdate(
            "insert into sprint(id,name,status,total_story_points,project_id,version)"
                + " values (1,'A','Active',0,1,0), (2,'B','Active',0,1,0)");
        assertThatThrownBy(() -> db.migrateTo("8"))
            .isInstanceOf(FlywayException.class)
            .hasStackTraceContaining("Multiple active sprints exist");
        try (var rows = sql.executeQuery("select count(*) from sprint where status = 'Active'")) {
          rows.next();
          assertThat(rows.getLong(1)).as("no sprint status was changed").isEqualTo(2);
        }
      }
    }
  }

  @Test
  void partialIndexAllowsOneActiveSprintPerProjectButIgnoresTrashedOnes() throws Exception {
    try (var db = new MigrationFixture()) {
      db.migrateTo(null);
      try (Statement sql = db.connection().createStatement()) {
        sql.executeUpdate(
            "insert into projects(id,name,owner_email,version,key) values"
                + " (1,'P','o@example.com',0,'PP'), (2,'Q','o@example.com',0,'QQ')");
        sql.executeUpdate(
            "insert into sprint(id,name,status,total_story_points,project_id,version,trashed_at)"
                + " values (1,'A','Active',0,1,0,null), (2,'Old','Active',0,1,0,now()),"
                + " (3,'Other','Active',0,2,0,null)");
        assertThatThrownBy(
                () ->
                    sql.executeUpdate(
                        "insert into sprint(id,name,status,total_story_points,project_id,version)"
                            + " values (4,'Second','Active',0,1,0)"))
            .hasMessageContaining("sprint_one_active_project_idx");
      }
    }
  }

  @Test
  void searchUpgradeIndexesExistingContentWithoutChangingVersions() throws Exception {
    try (var db = new MigrationFixture()) {
      db.migrateTo("15");
      try (Statement sql = db.connection().createStatement()) {
        sql.executeUpdate(
            "insert into users(id,email,password) values" + " (1,'migration@example.com','hash')");
        sql.executeUpdate(
            "insert into projects(id,name,owner_email,version,key) values"
                + " (10,'Migration project','migration@example.com',4,'MP')");
        sql.executeUpdate(
            "insert into project_member(project_id,email,role,user_id) values"
                + " (10,'migration@example.com','Owner',1)");
        sql.executeUpdate(
            "insert into task(id,project_id,number,title,status,is_blocked,story_points,version)"
                + " values (20,10,1,'Migration task','To Do',false,3,7)");
        sql.executeUpdate(
            "insert into"
                + " user_stories(id,project_id,title,acceptance_criteria,story_points,priority_id,version)"
                + " values (30,10,'Migration story','Complete',2,2,5)");
        sql.executeUpdate(
            "insert into task_comment(task_id,project_id,author_email,message) values"
                + " (20,10,'migration@example.com','Migration comment')");
        assertThat(db.migrateTo("18").migrationsExecuted).isEqualTo(3);
        assertThat(db.flyway().info().current().getVersion().toString()).isEqualTo("18");
        // Later phases may add migrations; this test keeps its B9 boundary explicit.
        db.migrateTo(null);
        db.flyway().validate();
        for (String table : java.util.List.of("task", "user_stories", "task_comment", "projects")) {
          try (var rows =
              sql.executeQuery(
                  "select count(*) from "
                      + table
                      + " where search_vector @@ to_tsquery('simple','migration:*')")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).as("Existing %s content is searchable", table).isEqualTo(1);
          }
        }
        try (var rows = sql.executeQuery("select title,version from task where id=20")) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getString(1)).isEqualTo("Migration task");
          assertThat(rows.getLong(2)).isEqualTo(7);
        }
        sql.executeUpdate(
            "insert into epic(project_id,title,created_at) values (10,'Migration epic',now())");
        try (var rows =
            sql.executeQuery(
                "select count(*) from epic where search_vector @@"
                    + " to_tsquery('simple','migration:*')")) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getInt(1)).isEqualTo(1);
        }
      }
    }
  }
}

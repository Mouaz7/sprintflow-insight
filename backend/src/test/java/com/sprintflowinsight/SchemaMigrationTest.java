package com.sprintflowinsight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprintflowinsight.support.MigrationFixture;
import java.sql.Statement;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

/**
 * Upgrade safety: every migration must apply on top of data written by the previous release,
 * and rules enforced by the schema must hold on the real engine. Each test uses its own schema.
 */
class SchemaMigrationTest {
  /** The newest migration shipped by the previous release (main before A1). */
  private static final String PREVIOUS_RELEASE = "12";

  @Test
  void upgradingFromThePreviousReleaseKeepsExistingDataUsable() throws Exception {
    try (var db = new MigrationFixture()) {
      db.migrateTo(PREVIOUS_RELEASE);
      try (Statement sql = db.connection().createStatement()) {
        sql.executeUpdate(
            "insert into users(id,email,password,display_name) values"
                + " (1,'owner@example.com','$2a$12$hash','Owner')");
        sql.executeUpdate(
            "insert into projects(id,name,owner_email,version) values (101,'Existing','owner@example.com',3)");
        sql.executeUpdate(
            "insert into project_member(id,project_id,email,role) values (1,101,'owner@example.com','Owner')");
        sql.executeUpdate(
            "insert into sprint(id,name,status,total_story_points,project_id,version,start_date,end_date)"
                + " values (11,'Sprint 1','Active',8,101,2,date '2026-01-05',date '2026-01-19')");
        sql.executeUpdate(
            "insert into user_stories(id,title,acceptance_criteria,story_points,priority_id,project_id,sprint_id,version,backlog_position)"
                + " values (201,'Story','Done when done',5,2,101,11,4,0)");
        sql.executeUpdate(
            "insert into task(id,title,status,is_blocked,project_id,sprint_id,story_id,story_points,version)"
                + " values (301,'Task','In Progress',false,101,11,201,3,6)");

        var result = db.migrateTo(null);
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isPositive();

        try (var rows =
            sql.executeQuery(
                "select p.version, s.status, us.backlog_position, t.version"
                    + " from projects p join sprint s on s.project_id = p.id"
                    + " join user_stories us on us.sprint_id = s.id join task t on t.story_id = us.id")) {
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
      }
    }
  }

  @Test
  void addingManualOrderPreservesExistingStoriesAndVersions() throws Exception {
    try (var db = new MigrationFixture()) {
      db.migrateTo("11");
      try (Statement sql = db.connection().createStatement()) {
        sql.executeUpdate(
            "insert into projects(id,name,owner_email,version) values (101,'Existing','owner@example.com',0)");
        sql.executeUpdate(
            "insert into user_stories(id,title,acceptance_criteria,story_points,priority_id,project_id,version)"
                + " values (201,'Preserved story','Unchanged',5,2,101,7)");
        db.migrateTo(null);
        try (var rows =
            sql.executeQuery("select id,title,story_points,version,backlog_position from user_stories")) {
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
            "insert into projects(id,name,owner_email,version) values (1,'P','o@example.com',0), (2,'Q','o@example.com',0)");
        sql.executeUpdate(
            "insert into sprint(id,name,status,total_story_points,project_id,version,trashed_at)"
                + " values (1,'A','Active',0,1,0,null), (2,'Old','Active',0,1,0,now()), (3,'Other','Active',0,2,0,null)");
        assertThatThrownBy(
                () ->
                    sql.executeUpdate(
                        "insert into sprint(id,name,status,total_story_points,project_id,version)"
                            + " values (4,'Second','Active',0,1,0)"))
            .hasMessageContaining("sprint_one_active_project_idx");
      }
    }
  }
}

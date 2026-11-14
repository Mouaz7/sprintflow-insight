package com.sprintflowinsight;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class BacklogMigrationTest {
  @Test
  void addingManualOrderPreservesExistingStoriesAndVersions() throws Exception {
    String url =
        System.getenv()
            .getOrDefault(
                "SPRINTFLOW_TEST_DB_URL", "jdbc:h2:mem:upgrade;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
    String user = System.getenv().getOrDefault("SPRINTFLOW_DB_USER", "sprintflow");
    String password = System.getenv().getOrDefault("SPRINTFLOW_DB_PASSWORD", "");
    String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = DriverManager.getConnection(url, user, password)) {
      try {
        Flyway.configure()
            .dataSource(url, user, password)
            .schemas(schema)
            .defaultSchema(schema)
            .target("11")
            .load()
            .migrate();
        connection.setSchema(schema);
        try (var statement = connection.createStatement()) {
          statement.executeUpdate(
              "insert into projects(id,name,owner_email,version) values"
                  + " (101,'Existing','owner@example.com',0)");
          statement.executeUpdate(
              "insert into"
                  + " user_stories(id,title,acceptance_criteria,story_points,priority_id,project_id,version)"
                  + " values (201,'Preserved story','Unchanged',5,2,101,7)");
          Flyway.configure()
              .dataSource(url, user, password)
              .schemas(schema)
              .defaultSchema(schema)
              .load()
              .migrate();
          try (var rows =
              statement.executeQuery(
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
      } finally {
        connection.setSchema(url.startsWith("jdbc:h2:") ? "PUBLIC" : "public");
        try (var statement = connection.createStatement()) {
          statement.execute("drop schema if exists \"" + schema + "\" cascade");
        }
      }
    }
  }
}

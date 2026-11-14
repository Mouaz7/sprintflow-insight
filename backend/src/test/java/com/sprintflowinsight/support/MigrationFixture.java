package com.sprintflowinsight.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

/**
 * An isolated, empty schema in the shared test database, for migrating step by step. Dropped
 * again by {@link #close()}.
 */
public final class MigrationFixture implements AutoCloseable {
  private final String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
  private final Connection connection;

  public MigrationFixture() throws SQLException {
    var db = PostgresTestContainer.POSTGRES;
    connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
    try (var statement = connection.createStatement()) {
      statement.execute("create schema \"" + schema + "\"");
    }
    connection.setSchema(schema);
  }

  /** Migrates up to and including {@code version}, or to the latest when {@code null}. */
  public MigrateResult migrateTo(String version) {
    var db = PostgresTestContainer.POSTGRES;
    var configuration =
        Flyway.configure()
            .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
            .schemas(schema)
            .defaultSchema(schema);
    if (version != null) configuration.target(version);
    return configuration.load().migrate();
  }

  public Connection connection() {
    return connection;
  }

  @Override
  public void close() throws SQLException {
    try (var statement = connection.createStatement()) {
      statement.execute("drop schema if exists \"" + schema + "\" cascade");
    } finally {
      connection.close();
    }
  }
}

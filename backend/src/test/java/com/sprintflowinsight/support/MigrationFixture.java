package com.sprintflowinsight.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * An isolated, empty schema in the shared test database, for migrating step by step. Dropped again
 * by {@link #close()}.
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
    var configuration = configuration();
    if (version != null) configuration.target(version);
    return configuration.load().migrate();
  }

  /** Actual old Java V8 plus the unchanged SQL migrations through V12. */
  public MigrateResult migratePreviousRelease(Path directory) throws Exception {
    for (var resource :
        new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/*.sql")) {
      if ("V8__single_active_sprint.sql".equals(resource.getFilename())) continue;
      try (var input = resource.getInputStream()) {
        Files.copy(input, directory.resolve(resource.getFilename()));
      }
    }
    return configuration()
        .locations("filesystem:" + directory)
        .javaMigrations(new com.sprintflowinsight.support.legacy.V8__single_active_sprint())
        .target("12")
        .load()
        .migrate();
  }

  public Flyway flyway() {
    return configuration().load();
  }

  private FluentConfiguration configuration() {
    var db = PostgresTestContainer.POSTGRES;
    return Flyway.configure()
        .dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
        .schemas(schema)
        .defaultSchema(schema);
  }

  /** A data source whose connections use this fixture's schema; one connection per call. */
  public javax.sql.DataSource dataSource() {
    var db = PostgresTestContainer.POSTGRES;
    var source = new org.postgresql.ds.PGSimpleDataSource();
    source.setUrl(db.getJdbcUrl());
    source.setUser(db.getUsername());
    source.setPassword(db.getPassword());
    source.setCurrentSchema(schema);
    return source;
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

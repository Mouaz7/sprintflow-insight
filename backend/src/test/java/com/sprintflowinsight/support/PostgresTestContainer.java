package com.sprintflowinsight.support;

import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One PostgreSQL container for the whole test JVM, wired into every Spring test context through
 * {@link JdbcConnectionDetails}. Tests therefore run against the same engine, version and SQL
 * dialect as production. Contexts share the database, so tests create uniquely named data and never
 * assume an empty table.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestContainer {
  public static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
              DockerImageName.parse(
                      System.getProperty("postgres.test.image", "postgres:16.10-alpine"))
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("sprintflow_test")
          .withUsername("sprintflow")
          .withPassword("test-only")
          // fsync off is safe for throwaway data and makes the suite markedly faster.
          .withCommand(
              "postgres",
              "-c",
              "fsync=off",
              "-c",
              "synchronous_commit=off",
              "-c",
              "max_connections=300");

  static {
    POSTGRES.start(); // stopped by the Testcontainers reaper when the JVM exits
  }

  /**
   * Expose connection details instead of a container bean. Boot manages container beans even with
   * destroyMethod="", which would stop this shared singleton when the first cached context closes.
   * The JUnit shutdown listener closes all contexts first; Ryuk then removes the container at exit.
   */
  @Bean
  JdbcConnectionDetails postgresConnectionDetails() {
    return new JdbcConnectionDetails() {
      @Override
      public String getJdbcUrl() {
        return POSTGRES.getJdbcUrl();
      }

      @Override
      public String getUsername() {
        return POSTGRES.getUsername();
      }

      @Override
      public String getPassword() {
        return POSTGRES.getPassword();
      }

      @Override
      public String getDriverClassName() {
        return POSTGRES.getDriverClassName();
      }
    };
  }
}

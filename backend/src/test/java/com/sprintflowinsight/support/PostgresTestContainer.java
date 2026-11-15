package com.sprintflowinsight.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One PostgreSQL container for the whole test JVM, wired into every Spring test context through
 * {@link ServiceConnection}. Tests therefore run against the same engine, version and SQL
 * dialect as production. Contexts share the database, so tests create uniquely named data and
 * never assume an empty table.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestContainer {
  public static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse(System.getProperty("postgres.test.image", "postgres:16.10-alpine"))
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("sprintflow_test")
          .withUsername("sprintflow")
          .withPassword("test-only")
          // fsync off is safe for throwaway data and makes the suite markedly faster.
          .withCommand("postgres", "-c", "fsync=off", "-c", "synchronous_commit=off", "-c", "max_connections=300");

  static {
    POSTGRES.start(); // stopped by the Testcontainers reaper when the JVM exits
  }

  /** Never stopped by a closing context: other cached contexts still use it. */
  @Bean(destroyMethod = "")
  @ServiceConnection
  PostgreSQLContainer<?> postgres() {
    return POSTGRES;
  }
}

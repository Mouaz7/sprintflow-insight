package com.sprintflowinsight.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * Module listeners run on the async executor and each hold a database connection. With virtual
 * threads that executor is unbounded unless configured, so the shipped limit must leave
 * connections for HTTP requests.
 */
class ListenerConcurrencyLimitTest {
  private static final String VIRTUAL = "spring.threads.virtual.enabled";
  private static final String LIMIT = "spring.task.execution.simple.concurrency-limit";
  private static final String POOL = "spring.datasource.hikari.maximum-pool-size";

  @Test
  void asyncListenerExecutorIsBoundedBelowTheConnectionPool() throws Exception {
    Properties shipped =
        PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
    int poolSize = Integer.parseInt(shipped.getProperty(POOL).replaceAll(".*:(\\d+)}$", "$1"));

    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
        .withPropertyValues(
            VIRTUAL + "=" + shipped.getProperty(VIRTUAL),
            LIMIT + "=" + shipped.getProperty(LIMIT))
        .run(
            context -> {
              var executor =
                  context.getBean("applicationTaskExecutor", SimpleAsyncTaskExecutor.class);
              assertThat(executor.isThrottleActive()).isTrue();
              assertThat(executor.getConcurrencyLimit()).isPositive().isLessThan(poolSize / 2 + 1);
            });
  }
}

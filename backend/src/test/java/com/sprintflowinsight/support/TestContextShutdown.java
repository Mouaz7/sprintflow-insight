package com.sprintflowinsight.support;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.platform.launcher.TestPlan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.TestContext;

/**
 * Close cached Spring contexts before JVM shutdown starts the Testcontainers reaper. Modulith's
 * registry queries unfinished publications during close and therefore still needs PostgreSQL.
 */
public class TestContextShutdown
    implements org.springframework.test.context.TestExecutionListener,
        org.junit.platform.launcher.TestExecutionListener {
  private static final Set<ConfigurableApplicationContext> CONTEXTS = new LinkedHashSet<>();

  @Override
  public void afterTestClass(TestContext testContext) {
    if (testContext.hasApplicationContext()
        && testContext.getApplicationContext() instanceof ConfigurableApplicationContext context) {
      synchronized (CONTEXTS) {
        CONTEXTS.add(context);
      }
    }
  }

  @Override
  public void testPlanExecutionFinished(TestPlan testPlan) {
    synchronized (CONTEXTS) {
      for (var context : CONTEXTS) if (context.isActive()) context.close();
      CONTEXTS.clear();
    }
  }
}

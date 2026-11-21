package com.sprintflowinsight.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class BusinessMetrics {
  private final Counter tasksCreated;
  private final Counter realtimePublished;

  public BusinessMetrics(MeterRegistry registry) {
    tasksCreated =
        Counter.builder("sprintflow.tasks")
            .description("Tasks created in committed transactions")
            .register(registry);
    realtimePublished =
        Counter.builder("sprintflow.realtime.published")
            .description("Committed events published to the realtime broker")
            .register(registry);
  }

  public void taskCreated() {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            tasksCreated.increment();
          }
        });
  }

  public void realtimePublished() {
    realtimePublished.increment();
  }
}

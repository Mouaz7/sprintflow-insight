package com.sprintflowinsight.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.support.ApiIntegrationTest;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** The full WebSocket context must retain bounded listeners and allow nested outbox delivery. */
class RealtimeExecutorIntegrationTest extends ApiIntegrationTest {
  @Autowired
  @Qualifier("applicationTaskExecutor")
  ThreadPoolTaskExecutor executor;

  @Autowired Environment environment;

  @Test
  void nestedPublicationsQueueWithoutBlockingTheBoundedVirtualWorkers() throws Exception {
    int limit = executor.getMaxPoolSize();
    int pool =
        environment.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class, 10);
    assertThat(limit).isPositive().isLessThan(pool / 2 + 1);
    assertThat(executor.getQueueCapacity()).isEqualTo(1000);
    var started = new CountDownLatch(limit);
    var publish = new CountDownLatch(1);
    var delivered = new CountDownLatch(limit);
    var virtual = new ConcurrentLinkedQueue<Boolean>();
    try {
      for (int i = 0; i < limit; i++)
        executor.execute(
            () -> {
              virtual.add(Thread.currentThread().isVirtual());
              started.countDown();
              try {
                if (publish.await(10, TimeUnit.SECONDS)) executor.execute(delivered::countDown);
              } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
              }
            });
      assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
      publish.countDown();
      assertThat(delivered.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(virtual).hasSize(limit).containsOnly(true);
    } finally {
      publish.countDown();
    }
  }
}

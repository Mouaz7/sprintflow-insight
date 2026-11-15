package com.sprintflowinsight.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.support.MigrationFixture;
import com.sprintflowinsight.workspace.task.TaskNumberAllocator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Concurrency property of task numbering on real PostgreSQL: however many creators race, each
 * receives a distinct number and together they use exactly 1..n.
 */
class TaskNumberAllocationIntegrationTest {
  private static MigrationFixture db;
  private static JdbcClient jdbc;
  private static final AtomicInteger PROJECTS = new AtomicInteger();

  @BeforeContainer
  static void migrate() throws Exception {
    db = new MigrationFixture();
    db.migrateTo(null);
    jdbc = JdbcClient.create(db.dataSource());
  }

  @AfterContainer
  static void drop() throws Exception {
    if (db != null) db.close();
  }

  private static long project(String key) {
    return jdbc.sql(
            "insert into projects(name, owner_email, version, key) values (?, 'o@example.com', 0, ?)"
                + " returning id")
        .params("Numbers " + UUID.randomUUID(), key)
        .query(Long.class)
        .single();
  }

  private static String key(int index) {
    StringBuilder key = new StringBuilder("N");
    for (int remaining = index; key.length() < 8; remaining /= 26)
      key.append((char) ('A' + remaining % 26));
    return key.toString();
  }

  @Property(tries = 15)
  void concurrentAllocationsNeverDuplicate(
      @ForAll @IntRange(min = 2, max = 12) int threads,
      @ForAll @IntRange(min = 1, max = 15) int perThread)
      throws Exception {
    String key = key(PROJECTS.incrementAndGet());
    long projectId = project(key);
    TaskNumberAllocator allocator = new TaskNumberAllocator(jdbc);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      List<Future<List<TaskNumberAllocator.Allocation>>> futures = new ArrayList<>();
      for (int t = 0; t < threads; t++)
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  List<TaskNumberAllocator.Allocation> mine = new ArrayList<>();
                  for (int i = 0; i < perThread; i++) mine.add(allocator.next(projectId));
                  return mine;
                }));
      start.countDown();
      List<Integer> numbers = new ArrayList<>();
      for (var future : futures)
        for (var allocation : future.get()) {
          assertThat(allocation.projectKey()).isEqualTo(key);
          numbers.add(allocation.number());
        }
      int total = threads * perThread;
      assertThat(numbers).doesNotHaveDuplicates().hasSize(total);
      assertThat(numbers.stream().mapToInt(Integer::intValue).max().orElseThrow())
          .isEqualTo(total);
    } finally {
      pool.shutdownNow();
    }
  }
}

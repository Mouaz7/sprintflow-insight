package com.sprintflowinsight.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.support.MigrationFixture;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Concurrency properties of the realtime sequence on real PostgreSQL. However many publishers race,
 * and even when the same module event is delivered twice at once, each project's numbers are handed
 * out exactly once, strictly increase in every publisher's own order, and the replay log holds the
 * contiguous range 1..n.
 */
class RealtimeSequenceIntegrationTest {
  private static MigrationFixture db;
  private static JdbcTemplate jdbc;
  private static TransactionTemplate transactions;
  private static RealtimeEventLog log;
  private static final AtomicInteger PROJECTS = new AtomicInteger();

  @BeforeContainer
  static void migrate() throws Exception {
    db = new MigrationFixture();
    db.migrateTo(null);
    var source = db.dataSource();
    jdbc = new JdbcTemplate(source);
    transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
    log = new RealtimeEventLog(jdbc, 1000, Duration.ofDays(7));
  }

  @AfterContainer
  static void drop() throws Exception {
    if (db != null) db.close();
  }

  private static long project() {
    int index = PROJECTS.incrementAndGet();
    StringBuilder key = new StringBuilder("RT");
    for (int remaining = index; key.length() < 8; remaining /= 26)
      key.append((char) ('A' + remaining % 26));
    return jdbc.queryForObject(
        "insert into projects(name, owner_email, version, key) values (?, 'o@example.com', 0, ?)"
            + " returning id",
        Long.class,
        "Realtime " + UUID.randomUUID(),
        key.toString());
  }

  private record Published(long projectId, long seq) {}

  /** Appends in its own transaction; a concurrent duplicate loses on the unique index. */
  private static Optional<RealtimeMessage> append(UUID source, long projectId) {
    try {
      return transactions.execute(
          status -> log.append(source, projectId, "TaskUpdated", 1L, 1L, Instant.now()));
    } catch (DuplicateKeyException replayedConcurrently) {
      return Optional.empty();
    }
  }

  @Property(tries = 12)
  void sequenceNumbersStrictlyIncreasePerProjectUnderConcurrentPublishing(
      @ForAll @IntRange(min = 1, max = 3) int projectCount,
      @ForAll @IntRange(min = 2, max = 8) int publishers,
      @ForAll @Size(min = 1, max = 25) List<@IntRange(min = 0, max = 2) Integer> targets,
      @ForAll boolean deliverTwice)
      throws Exception {
    List<Long> projects = new ArrayList<>();
    for (int i = 0; i < projectCount; i++) projects.add(project());
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(publishers);
    try {
      // Every publisher sends the same events in the same order; with deliverTwice each event id
      // is shared by two publishers, as when the registry replays an event that is still running.
      List<Future<List<Published>>> futures = new ArrayList<>();
      List<UUID> ids = new ArrayList<>();
      for (int i = 0; i < targets.size() * publishers; i++) ids.add(UUID.randomUUID());
      for (int p = 0; p < publishers; p++) {
        int publisher = p;
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  List<Published> mine = new ArrayList<>();
                  for (int i = 0; i < targets.size(); i++) {
                    long projectId = projects.get(targets.get(i) % projectCount);
                    int owner = deliverTwice ? publisher / 2 : publisher;
                    UUID source = ids.get(owner * targets.size() + i);
                    append(source, projectId)
                        .ifPresent(message -> mine.add(new Published(projectId, message.seq())));
                  }
                  return mine;
                }));
      }
      start.countDown();

      Map<Long, List<Long>> seqs = new HashMap<>();
      for (var future : futures) {
        List<Published> mine = future.get();
        Map<Long, Long> last = new HashMap<>();
        for (Published published : mine) {
          Long previous = last.put(published.projectId(), published.seq());
          if (previous != null)
            assertThat(published.seq()).as("one publisher's order").isGreaterThan(previous);
          seqs.computeIfAbsent(published.projectId(), id -> new ArrayList<>()).add(published.seq());
        }
      }

      for (long projectId : projects) {
        List<Long> handed = new ArrayList<>(seqs.getOrDefault(projectId, List.of()));
        Collections.sort(handed);
        long n = handed.size();
        assertThat(handed).containsExactlyElementsOf(LongStream.rangeClosed(1, n).boxed().toList());
        RealtimeEventPage page = log.since(projectId, 0);
        assertThat(page.latestSeq()).isEqualTo(n);
        assertThat(page.resync()).isFalse();
        assertThat(page.events())
            .extracting(RealtimeMessage::seq)
            .containsExactlyElementsOf(handed);
      }
      long distinctEvents =
          targets.size() * (long) (deliverTwice ? (publishers + 1) / 2 : publishers);
      assertThat(seqs.values().stream().mapToLong(List::size).sum()).isEqualTo(distinctEvents);
    } finally {
      pool.shutdownNow();
    }
  }

  private static RealtimeMessage appendAt(long projectId, Instant at) {
    return transactions
        .execute(status -> log.append(UUID.randomUUID(), projectId, "TaskMoved", 7L, 3L, at))
        .orElseThrow();
  }

  @Example
  void gapRequestsReturnTheMissedRangeOrAskForAResync() {
    long projectId = project();
    assertThat(log.since(projectId, 0))
        .isEqualTo(new RealtimeEventPage(projectId, 0, false, List.of()));
    for (int i = 0; i < 3; i++) appendAt(projectId, Instant.now());

    var page = log.since(projectId, 1);
    assertThat(page.resync()).isFalse();
    assertThat(page.events())
        .containsExactly(
            new RealtimeMessage(projectId, 2, "TaskMoved", 7L, 3L),
            new RealtimeMessage(projectId, 3, "TaskMoved", 7L, 3L));
    assertThat(log.since(projectId, 3).events()).isEmpty();
    assertThat(log.since(projectId, 3).resync()).isFalse();
    // A client ahead of the server (for example after a restore) must reload.
    assertThat(log.since(projectId, 4))
        .isEqualTo(new RealtimeEventPage(projectId, 3, true, List.of()));
    assertThat(log.since(projectId, -1).resync()).isTrue();
  }

  @Example
  void retentionPrunesOldRowsAndGapsIntoThemNeedAResync() {
    long projectId = project();
    Instant now = Instant.now();
    appendAt(projectId, now.minus(Duration.ofDays(8)));
    appendAt(projectId, now);
    Integer pruned = transactions.execute(status -> log.prune(now));
    assertThat(pruned).isPositive();

    assertThat(log.since(projectId, 0))
        .isEqualTo(new RealtimeEventPage(projectId, 2, true, List.of()));
    assertThat(log.since(projectId, 1).events())
        .extracting(RealtimeMessage::seq)
        .containsExactly(2L);
  }

  @Example
  void largeGapsAndTheCountLimitAskForAResync() {
    long projectId = project();
    var bounded = new RealtimeEventLog(jdbc, RealtimeEventLog.PAGE_SIZE, Duration.ofDays(7));
    for (int i = 0; i < RealtimeEventLog.PAGE_SIZE + 5; i++)
      transactions.execute(
          status ->
              bounded.append(UUID.randomUUID(), projectId, "TaskCreated", 1L, 0L, Instant.now()));
    long latest = RealtimeEventLog.PAGE_SIZE + 5;
    assertThat(
            jdbc.queryForObject(
                "select count(*) from realtime_event where project_id=?", Long.class, projectId))
        .isEqualTo(RealtimeEventLog.PAGE_SIZE);
    assertThat(bounded.since(projectId, 0).resync()).isTrue();
    assertThat(bounded.since(projectId, 5).events()).hasSize(RealtimeEventLog.PAGE_SIZE);
    assertThat(bounded.since(projectId, latest - 1).events()).hasSize(1);
  }

  @Example
  void eventsForDeletedProjectsAreDroppedAndDeletionRemovesTheLog() {
    long projectId = project();
    appendAt(projectId, Instant.now());
    jdbc.update("delete from projects where id=?", projectId);
    Optional<RealtimeMessage> dropped =
        transactions.execute(
            status -> log.append(UUID.randomUUID(), projectId, "TaskMoved", 1L, 1L, Instant.now()));
    assertThat(dropped).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from realtime_event where project_id=?", Long.class, projectId))
        .isZero();
    assertThat(log.since(projectId, 0).latestSeq()).isZero();
  }

  @Example
  void replayedEventsKeepTheirFirstSequenceNumber() {
    long projectId = project();
    UUID source = UUID.randomUUID();
    assertThat(append(source, projectId)).map(RealtimeMessage::seq).contains(1L);
    assertThat(append(source, projectId)).isEmpty();
    assertThat(appendAt(projectId, Instant.now()).seq()).isEqualTo(2);
  }
}

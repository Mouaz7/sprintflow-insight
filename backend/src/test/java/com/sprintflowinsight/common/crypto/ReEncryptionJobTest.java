package com.sprintflowinsight.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Batching and bookkeeping of the rotation job against an in-memory table. The SQL itself runs on
 * PostgreSQL in {@code ReEncryptionIntegrationTest}.
 */
class ReEncryptionJobTest {
  private final byte[] oldKey = EncryptionServiceTest.key();
  private final byte[] newKey = EncryptionServiceTest.key();
  private final EncryptionService before = new EncryptionService(Keyring.of(oldKey));
  private final EncryptionService after = new EncryptionService(Keyring.of(newKey, oldKey));

  /** A fake table: id → value, honouring the job's "not current, id > after, limit" select. */
  static final class FakeTable extends JdbcTemplate {
    final Map<String, TreeMap<Long, String>> tables = new LinkedHashMap<>();
    final List<String> statements = new ArrayList<>();
    int commits;

    TreeMap<Long, String> table(String sql) {
      for (var entry : tables.entrySet())
        if (sql.contains(" " + entry.getKey() + " ")) return entry.getValue();
      throw new AssertionError("unknown table in " + sql);
    }

    @Override
    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
      statements.add(sql);
      String prefix = ((String) args[0]).replace("%", "");
      long from = (Long) args[1];
      int limit = (Integer) args[2];
      List<T> rows = new ArrayList<>();
      for (var row : table(sql).tailMap(from, false).entrySet()) {
        if (rows.size() == limit) break;
        if (row.getValue() == null || row.getValue().startsWith(prefix)) continue;
        try {
          ResultSet result = mock(ResultSet.class);
          when(result.getLong(1)).thenReturn(row.getKey());
          when(result.getString(2)).thenReturn(row.getValue());
          rows.add(mapper.mapRow(result, rows.size()));
        } catch (SQLException impossible) {
          throw new AssertionError(impossible);
        }
      }
      return rows;
    }

    @Override
    public int update(String sql, Object... args) {
      statements.add(sql);
      table(sql).put((Long) args[1], (String) args[0]);
      return 1;
    }

    @Override
    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
      String prefix = ((String) args[0]).replace("%", "");
      long count =
          table(sql).values().stream().filter(v -> v != null && !v.startsWith(prefix)).count();
      return type.cast(count);
    }
  }

  private PlatformTransactionManager transactions(FakeTable table) {
    return new PlatformTransactionManager() {
      public TransactionStatus getTransaction(TransactionDefinition definition) {
        return new SimpleTransactionStatus();
      }

      public void commit(TransactionStatus status) {
        table.commits++;
      }

      public void rollback(TransactionStatus status) {}
    };
  }

  private ReEncryptionJob job(FakeTable table, int batchSize, EncryptedColumn... columns) {
    StaticListableBeanFactory beans = new StaticListableBeanFactory();
    for (int i = 0; i < columns.length; i++) beans.addBean("column" + i, columns[i]);
    return new ReEncryptionJob(
        after, table, transactions(table), beans.getBeanProvider(EncryptedColumn.class), batchSize);
  }

  @Test
  void rotatesEveryOldValueInBatchesAndSkipsCurrentAndNullValues() {
    FakeTable table = new FakeTable();
    TreeMap<Long, String> secrets = new TreeMap<>();
    for (long id = 1; id <= 7; id++) secrets.put(id, before.encrypt("secret-" + id, "hook.secret"));
    secrets.put(8L, null);
    secrets.put(9L, after.encrypt("already-current", "hook.secret"));
    table.tables.put("hook", secrets);
    ReEncryptionJob job = job(table, 3, EncryptedColumn.of("hook", "id", "secret"));

    assertThat(job.pending()).isEqualTo(7);
    ReEncryptionJob.Report report = job.run();

    assertThat(report).isEqualTo(new ReEncryptionJob.Report(7, 0));
    assertThat(job.pending()).isZero();
    for (long id = 1; id <= 7; id++) {
      assertThat(after.isCurrent(secrets.get(id))).isTrue();
      assertThat(after.decrypt(secrets.get(id), "hook.secret")).isEqualTo("secret-" + id);
    }
    assertThat(secrets.get(8L)).isNull();
    assertThat(after.decrypt(secrets.get(9L), "hook.secret")).isEqualTo("already-current");
    // 3 + 3 + 1 rows: the short third batch ends the column without a fourth select.
    assertThat(table.commits).isEqualTo(3);
    assertThat(table.statements.getFirst())
        .contains("from hook where secret is not null and secret not like ?")
        .contains("for update skip locked");
    assertThat(job.run()).isEqualTo(new ReEncryptionJob.Report(0, 0));
  }

  @Test
  void countsUndecryptableValuesWithoutLoopingOrStoppingOtherColumns() {
    FakeTable table = new FakeTable();
    EncryptionService stranger = new EncryptionService(Keyring.of(EncryptionServiceTest.key()));
    TreeMap<Long, String> hooks = new TreeMap<>();
    String lost = stranger.encrypt("lost", "hook.secret");
    hooks.put(1L, lost);
    hooks.put(2L, before.encrypt("kept", "hook.secret"));
    hooks.put(3L, "garbage");
    TreeMap<Long, String> tokens = new TreeMap<>();
    tokens.put(10L, before.encrypt("token", "token.value"));
    table.tables.put("hook", hooks);
    table.tables.put("token", tokens);
    ReEncryptionJob job =
        job(
            table,
            2,
            EncryptedColumn.of("hook", "id", "secret"),
            EncryptedColumn.of("token", "id", "value"));

    assertThat(job.run()).isEqualTo(new ReEncryptionJob.Report(2, 2));
    assertThat(hooks.get(1L)).isEqualTo(lost);
    assertThat(after.decrypt(hooks.get(2L), "hook.secret")).isEqualTo("kept");
    assertThat(hooks.get(3L)).isEqualTo("garbage");
    assertThat(after.decrypt(tokens.get(10L), "token.value")).isEqualTo("token");
    assertThat(job.pending()).isEqualTo(2);
    job.scheduled();
  }

  @Test
  void runsWithNoRegisteredColumnsAndRejectsABadBatchSize() {
    FakeTable table = new FakeTable();
    assertThat(job(table, 10).run()).isEqualTo(new ReEncryptionJob.Report(0, 0));
    assertThat(job(table, 10).pending()).isZero();
    job(table, 10).scheduled();
    assertThatThrownBy(() -> job(table, 0)).isInstanceOf(IllegalArgumentException.class);
  }
}

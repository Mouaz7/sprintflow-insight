package com.sprintflowinsight.common.crypto;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Key rotation: rewrites every registered {@link EncryptedColumn} value that was not written with
 * the current key. Runs hourly and is idempotent; each batch is its own short transaction and locks
 * its rows with {@code FOR UPDATE SKIP LOCKED}, so concurrent instances and ordinary writes are
 * never blocked for long and never lose an update. A retired key may be removed from configuration
 * once {@link #pending()} reports zero for every column.
 */
@Component
public class ReEncryptionJob {
  private static final Logger LOG = LoggerFactory.getLogger(ReEncryptionJob.class);

  private final EncryptionService encryption;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final ObjectProvider<EncryptedColumn> columns;
  private final int batchSize;

  public ReEncryptionJob(
      EncryptionService encryption,
      JdbcTemplate jdbc,
      PlatformTransactionManager transactionManager,
      ObjectProvider<EncryptedColumn> columns,
      @Value("${app.encryption.reencrypt-batch-size:200}") int batchSize) {
    if (batchSize < 1) throw new IllegalArgumentException("Batch size must be positive.");
    this.encryption = encryption;
    this.jdbc = jdbc;
    this.transactions = new TransactionTemplate(transactionManager);
    this.columns = columns;
    this.batchSize = batchSize;
  }

  /** Outcome of one run. {@code failed} counts values no configured key could decrypt. */
  public record Report(long rotated, long failed) {
    Report plus(Report other) {
      return new Report(rotated + other.rotated, failed + other.failed);
    }
  }

  @Scheduled(cron = "${app.encryption.reencrypt-cron:0 23 * * * *}")
  public void scheduled() {
    Report report = run();
    if (report.rotated() > 0 || report.failed() > 0)
      LOG.info(
          "Re-encryption rotated {} values to key {}; {} could not be decrypted.",
          report.rotated(),
          encryption.currentKeyId(),
          report.failed());
  }

  public Report run() {
    Report total = new Report(0, 0);
    for (EncryptedColumn column : registered()) total = total.plus(rotate(column));
    return total;
  }

  /** Values per column that still use a retired (or unknown) key. */
  public long pending() {
    long pending = 0;
    for (EncryptedColumn column : registered()) {
      Long count =
          jdbc.queryForObject(
              "select count(*) from "
                  + column.table()
                  + " where "
                  + column.valueColumn()
                  + " is not null and "
                  + column.valueColumn()
                  + " not like ?",
              Long.class,
              encryption.currentPrefix() + "%");
      pending += count == null ? 0 : count;
    }
    return pending;
  }

  Report rotate(EncryptedColumn column) {
    String select =
        "select "
            + column.idColumn()
            + ", "
            + column.valueColumn()
            + " from "
            + column.table()
            + " where "
            + column.valueColumn()
            + " is not null and "
            + column.valueColumn()
            + " not like ? and "
            + column.idColumn()
            + " > ? order by "
            + column.idColumn()
            + " limit ? for update skip locked";
    String update =
        "update "
            + column.table()
            + " set "
            + column.valueColumn()
            + " = ? where "
            + column.idColumn()
            + " = ?";
    long after = Long.MIN_VALUE;
    Report total = new Report(0, 0);
    while (true) {
      long from = after;
      Batch batch =
          transactions.execute(
              status -> {
                List<Row> rows =
                    jdbc.query(
                        select,
                        (result, index) -> new Row(result.getLong(1), result.getString(2)),
                        encryption.currentPrefix() + "%",
                        from,
                        batchSize);
                long rotated = 0, failed = 0;
                for (Row row : rows) {
                  try {
                    jdbc.update(update, encryption.reEncrypt(row.value(), column.context()), row.id());
                    rotated++;
                  } catch (EncryptionException undecryptable) {
                    failed++;
                    LOG.warn(
                        "Cannot re-encrypt {}.{} row {}: {}",
                        column.table(),
                        column.valueColumn(),
                        row.id(),
                        undecryptable.getMessage());
                  }
                }
                long last = rows.isEmpty() ? from : rows.getLast().id();
                return new Batch(rows.size(), last, new Report(rotated, failed));
              });
      if (batch == null || batch.size() == 0) return total;
      total = total.plus(batch.report());
      after = batch.lastId();
      if (batch.size() < batchSize) return total;
    }
  }

  private List<EncryptedColumn> registered() {
    return new ArrayList<>(columns.orderedStream().toList());
  }

  private record Row(long id, String value) {}

  private record Batch(int size, long lastId, Report report) {}
}

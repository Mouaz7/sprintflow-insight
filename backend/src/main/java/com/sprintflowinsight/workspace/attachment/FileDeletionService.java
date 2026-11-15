package com.sprintflowinsight.workspace.attachment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FileDeletionService {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(FileDeletionService.class);
  private final JdbcTemplate jdbc;
  private final TransactionTemplate independent;
  private final Path directory = Path.of("data", "attachments").toAbsolutePath().normalize();

  public FileDeletionService(JdbcTemplate jdbc, PlatformTransactionManager manager) {
    this.jdbc = jdbc;
    independent = new TransactionTemplate(manager);
    independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueue(List<String> keys) {
    for (String key : keys)
      if (key != null)
        jdbc.update(
            "insert into file_deletion_job(storage_key,available_at) values (?,?)",
            key,
            Timestamp.from(Instant.now()));
    if (!keys.isEmpty())
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              drain();
            }
          });
  }

  @Scheduled(fixedDelayString = "${app.attachments.cleanup-delay-ms:60000}")
  public void drain() {
    try {
      independent.executeWithoutResult(
          tx -> {
            var jobs =
                jdbc.queryForList(
                    "select id,storage_key,attempts from file_deletion_job where available_at <= ?"
                        + " order by id limit 100",
                    Timestamp.from(Instant.now()));
            for (var job : jobs) {
              long id = ((Number) job.get("id")).longValue();
              try {
                Path path = directory.resolve((String) job.get("storage_key")).normalize();
                if (!path.startsWith(directory) || path.equals(directory))
                  throw new java.io.IOException("Invalid storage key");
                Files.deleteIfExists(path);
                jdbc.update("delete from file_deletion_job where id=?", id);
              } catch (java.io.IOException error) {
                int attempts = Math.min(((Number) job.get("attempts")).intValue() + 1, 30);
                jdbc.update(
                    "update file_deletion_job set attempts=?,available_at=? where id=?",
                    attempts,
                    Timestamp.from(Instant.now().plusSeconds(Math.min(3600, 60L * attempts))),
                    id);
                LOG.warn("Attachment cleanup job {} will be retried.", id);
              }
            }
          });
    } catch (RuntimeException error) {
      LOG.warn("Attachment cleanup is unavailable; queued jobs will be retried.");
    }
  }
}

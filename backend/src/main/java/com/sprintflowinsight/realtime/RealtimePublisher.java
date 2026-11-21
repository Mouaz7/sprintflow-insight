package com.sprintflowinsight.realtime;

import com.sprintflowinsight.common.BusinessMetrics;
import java.time.Instant;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Records a realtime message and sends it to the project topic once the recording transaction has
 * committed. A rolled-back listener therefore sends nothing and its sequence number is reused.
 */
@Component
public class RealtimePublisher {
  private final RealtimeEventLog log;
  private final SimpMessageSendingOperations broker;
  private final BusinessMetrics metrics;

  public RealtimePublisher(
      RealtimeEventLog log, SimpMessageSendingOperations broker, BusinessMetrics metrics) {
    this.log = log;
    this.broker = broker;
    this.metrics = metrics;
  }

  /** Must run inside a transaction; see {@link RealtimeEventLog#append}. */
  public void publish(
      UUID sourceEventId,
      Long projectId,
      String type,
      Long entityId,
      Long version,
      Instant occurredAt) {
    if (projectId == null || sourceEventId == null) return;
    log.append(sourceEventId, projectId, type, entityId, version, occurredAt)
        .ifPresent(
            message ->
                TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                      @Override
                      public void afterCommit() {
                        broker.convertAndSend(ProjectTopics.events(projectId), message);
                        metrics.realtimePublished();
                      }
                    }));
  }
}

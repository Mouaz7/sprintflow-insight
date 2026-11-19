package com.sprintflowinsight.notifications.digest;

import java.time.Instant;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DigestScheduler {
  private final DigestRepository repository;
  private final DigestMailer mailer;
  private final DigestWorker worker;

  public DigestScheduler(DigestRepository repository, DigestMailer mailer, DigestWorker worker) {
    this.repository = repository;
    this.mailer = mailer;
    this.worker = worker;
  }

  @Scheduled(
      fixedDelayString = "${app.digest.delay-ms:60000}",
      initialDelayString = "${app.digest.initial-delay-ms:60000}")
  @SchedulerLock(name = "email-digest", lockAtMostFor = "PT15M", lockAtLeastFor = "PT5S")
  public void run() {
    LockAssert.assertLocked();
    if (!mailer.ready()) return;
    var now = Instant.now();
    for (Long id : repository.due(now, PageRequest.of(0, 20))) worker.send(id, now);
  }
}

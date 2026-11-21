package com.sprintflowinsight.risk.live;

import java.time.Instant;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RiskTrainingJob {
  private static final Logger LOG = LoggerFactory.getLogger(RiskTrainingJob.class);

  private final JdbcTemplate jdbc;
  private final TaskRiskService service;

  RiskTrainingJob(JdbcTemplate jdbc, TaskRiskService service) {
    this.jdbc = jdbc;
    this.service = service;
  }

  @Scheduled(cron = "${app.risk.training-cron:0 40 2 * * *}", zone = "UTC")
  @SchedulerLock(name = "late-task-risk-training", lockAtMostFor = "PT2H")
  public void run() {
    LockAssert.assertLocked();
    long afterId = 0;
    while (true) {
      var projects =
          jdbc.queryForList(
              """
select p.id from projects p where p.id>? and p.archived_at is null and p.trashed_at is null
and (exists(select 1 from sprint s where s.project_id=p.id and s.status='Active'
      and s.trashed_at is null)
  or exists(select 1 from task_risk_observation o where o.project_id=p.id))
order by p.id limit 100
""",
              Long.class,
              afterId);
      if (projects.isEmpty()) return;
      for (long project : projects) {
        try {
          service.train(project, Instant.now());
        } catch (RuntimeException error) {
          LOG.warn("Nightly risk training failed for project {}", project, error);
        }
      }
      afterId = projects.getLast();
    }
  }
}

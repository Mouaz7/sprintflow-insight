package com.sprintflowinsight.risk.live;

import com.sprintflowinsight.risk.RiskModel;
import com.sprintflowinsight.risk.RiskModelTrainer;
import com.sprintflowinsight.risk.RiskPrediction;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class TaskRiskService {
  private final RiskModelTrainer trainer = new RiskModelTrainer();
  private final RiskModel initialModel = trainer.train(List.of());
  private final JdbcTemplate jdbc;
  private final ProjectAccessService access;
  private final RiskFeatureReader features;
  private final RiskHistory history;

  TaskRiskService(
      JdbcTemplate jdbc,
      ProjectAccessService access,
      RiskFeatureReader features,
      RiskHistory history) {
    this.jdbc = jdbc;
    this.access = access;
    this.features = features;
    this.history = history;
  }

  record Sprint(long id, LocalDate endDate) {
    long remainingDays(Instant now) {
      return ChronoUnit.DAYS.between(LocalDate.ofInstant(now, ZoneOffset.UTC), endDate);
    }
  }

  record TaskPrediction(long taskId, RiskPrediction prediction) {}

  record ProjectRisk(
      Long sprintId, Instant trainedAt, int exampleCount, List<TaskPrediction> tasks) {}

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ProjectRisk read(long projectId, HttpSession session) {
    access.read(projectId, session);
    Instant now = Instant.now();
    var active = activeSprint(projectId, now);
    if (active.isEmpty()) return new ProjectRisk(null, null, 0, List.of());
    Sprint sprint = active.orElseThrow();
    var stored = history.model(projectId);
    RiskModel model = stored.map(RiskHistory.StoredModel::model).orElse(initialModel);
    var predictions =
        features.read(projectId, sprint.id(), now, sprint.remainingDays(now)).stream()
            .map(row -> new TaskPrediction(row.taskId(), model.predict(row.features())))
            .toList();
    return new ProjectRisk(
        sprint.id(),
        stored.map(RiskHistory.StoredModel::trainedAt).orElse(null),
        stored.map(RiskHistory.StoredModel::exampleCount).orElse(0),
        predictions);
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public void train(long projectId, Instant now) {
    var active = activeSprint(projectId, now);
    active.ifPresent(
        sprint ->
            history.observe(
                projectId,
                sprint.id(),
                now,
                features.read(projectId, sprint.id(), now, sprint.remainingDays(now))));
    history.resolve(projectId, now);
    var examples = history.trainingExamples(projectId);
    history.save(projectId, now, examples.size(), trainer.train(examples));
  }

  private Optional<Sprint> activeSprint(long projectId, Instant now) {
    return jdbc
        .query(
            """
            select id,end_date from sprint where project_id=? and status='Active'
              and trashed_at is null and end_date>=?
            """,
            (row, index) -> new Sprint(row.getLong(1), row.getObject(2, LocalDate.class)),
            projectId,
            LocalDate.ofInstant(now, ZoneOffset.UTC))
        .stream()
        .findFirst();
  }
}

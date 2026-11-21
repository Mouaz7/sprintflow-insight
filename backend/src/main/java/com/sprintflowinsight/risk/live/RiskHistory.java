package com.sprintflowinsight.risk.live;

import com.sprintflowinsight.risk.LabelledExample;
import com.sprintflowinsight.risk.RiskFeatures;
import com.sprintflowinsight.risk.RiskModel;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RiskHistory {
  static final int MAX_TRAINING_ROWS = 5_000;

  private final JdbcTemplate jdbc;

  RiskHistory(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  record StoredModel(RiskModel model, Instant trainedAt, int exampleCount) {}

  void observe(long projectId, long sprintId, Instant now, List<RiskFeatureReader.Snapshot> rows) {
    jdbc.batchUpdate(
        """
        insert into task_risk_observation(project_id,sprint_id,task_id,observed_at,features)
        values(?,?,?,?,?) on conflict(project_id,sprint_id,task_id) do nothing
        """,
        rows,
        100,
        (statement, row) -> {
          statement.setLong(1, projectId);
          statement.setLong(2, sprintId);
          statement.setLong(3, row.taskId());
          statement.setTimestamp(4, Timestamp.from(now));
          statement.setObject(5, boxed(row.features().toVector()));
        });
  }

  void resolve(long projectId, Instant now) {
    jdbc.update(
        """
with outcomes as (
  select o.project_id,o.sprint_id,o.task_id,o.observed_at,
    least(s.completed_at,(s.end_date+1)::timestamp at time zone 'UTC') as finished_at
  from task_risk_observation o
  join sprint s on s.project_id=o.project_id and s.id=o.sprint_id
  join task t on t.project_id=o.project_id and t.id=o.task_id
  where o.project_id=? and o.late is null and s.trashed_at is null and t.trashed_at is null
    and s.status in ('Completed','Active')
)
update task_risk_observation o set resolved_at=r.finished_at,late=(
  select h.status is distinct from 'Done' from task_status_history h
  where h.project_id=r.project_id and h.task_id=r.task_id and h.changed_at<=r.finished_at
  order by h.changed_at desc,h.id desc limit 1
) is distinct from false
from outcomes r where o.project_id=r.project_id and o.sprint_id=r.sprint_id and o.task_id=r.task_id
  and r.finished_at>r.observed_at and r.finished_at<=?
""",
        projectId,
        Timestamp.from(now));
  }

  List<LabelledExample> trainingExamples(long projectId) {
    return jdbc.query(
        """
select o.features,o.late,o.observed_at from task_risk_observation o
join sprint s on s.project_id=o.project_id and s.id=o.sprint_id
join task t on t.project_id=o.project_id and t.id=o.task_id
where o.project_id=? and o.late is not null and t.trashed_at is null and s.trashed_at is null
  and s.status in ('Completed','Active')
order by o.observed_at desc,o.sprint_id,o.task_id limit ?
""",
        (row, index) -> {
          double[] f = vector(row, 1);
          var features =
              new RiskFeatures(
                  f[0], (int) f[1], (int) f[2], (int) f[3], f[4], (int) f[5], f[6], (int) f[7]);
          return new LabelledExample(features, row.getBoolean(2), row.getTimestamp(3).toInstant());
        },
        projectId,
        MAX_TRAINING_ROWS);
  }

  Optional<StoredModel> model(long projectId) {
    return jdbc
        .query(
            """
            select source,intercept,means,scales,weights,trained_at,example_count
            from task_risk_model where project_id=?
            """,
            (row, index) ->
                new StoredModel(
                    RiskModel.restore(
                        RiskModel.Source.valueOf(row.getString(1)),
                        row.getDouble(2),
                        vector(row, 3),
                        vector(row, 4),
                        vector(row, 5)),
                    row.getTimestamp(6).toInstant(),
                    row.getInt(7)),
            projectId)
        .stream()
        .findFirst();
  }

  void save(long projectId, Instant now, int examples, RiskModel model) {
    jdbc.update(
        """
insert into task_risk_model(project_id,trained_at,example_count,source,intercept,means,scales,weights)
values(?,?,?,?,?,?,?,?)
on conflict(project_id) do update set trained_at=excluded.trained_at,
  example_count=excluded.example_count,source=excluded.source,intercept=excluded.intercept,
  means=excluded.means,scales=excluded.scales,weights=excluded.weights
where task_risk_model.trained_at<=excluded.trained_at
""",
        projectId,
        Timestamp.from(now),
        examples,
        model.source().name(),
        model.intercept(),
        boxed(model.standardizer().means()),
        boxed(model.standardizer().scales()),
        boxed(model.weights()));
  }

  private static Double[] boxed(double[] vector) {
    return Arrays.stream(vector).boxed().toArray(Double[]::new);
  }

  private static double[] vector(ResultSet row, int column) throws SQLException {
    var array = row.getArray(column);
    try {
      return Arrays.stream((Double[]) array.getArray()).mapToDouble(Double::doubleValue).toArray();
    } finally {
      array.free();
    }
  }
}

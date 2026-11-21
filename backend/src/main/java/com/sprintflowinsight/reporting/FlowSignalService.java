package com.sprintflowinsight.reporting;

import com.sprintflowinsight.flow.EwmaControlChart;
import com.sprintflowinsight.flow.EwmaControlChart.Chart;
import com.sprintflowinsight.flow.EwmaControlChart.Point;
import com.sprintflowinsight.flow.EwmaControlChart.Signal;
import com.sprintflowinsight.workspace.events.NotificationRequested;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import jakarta.servlet.http.HttpSession;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class FlowSignalService {
  private final JdbcTemplate jdbc;
  private final ProjectAccessService access;
  private final ApplicationEventPublisher notifications;

  FlowSignalService(
      JdbcTemplate jdbc, ProjectAccessService access, ApplicationEventPublisher notifications) {
    this.jdbc = jdbc;
    this.access = access;
    this.notifications = notifications;
  }

  enum Metric {
    INFLOW,
    THROUGHPUT
  }

  record FlowSignals(LocalDate firstMonitoredDay, Chart inflow, Chart throughput) {}

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public FlowSignals read(long projectId, HttpSession session) {
    access.read(projectId, session);
    return snapshot(projectId, LocalDate.now(ZoneOffset.UTC));
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public void checkAndNotify(long projectId, Instant runAt) {
    LocalDate today = LocalDate.ofInstant(runAt, ZoneOffset.UTC);
    var results = snapshot(projectId, today);
    if (results.firstMonitoredDay() == null) return;
    recordSignal(projectId, today.minusDays(1), Metric.INFLOW, results.inflow(), runAt);
    recordSignal(projectId, today.minusDays(1), Metric.THROUGHPUT, results.throughput(), runAt);
  }

  private FlowSignals snapshot(long projectId, LocalDate end) {
    LocalDate start =
        end.minusDays(EwmaControlChart.BASELINE_PERIODS + EwmaControlChart.MONITOR_PERIODS);
    Boolean observed =
        jdbc.queryForObject(
            "select exists(select 1 from task where project_id=? and trashed_at is null and"
                + " created_at<=?)",
            Boolean.class,
            projectId,
            timestamp(start));
    if (!Boolean.TRUE.equals(observed)) return new FlowSignals(null, null, null);

    var incoming =
        counts(
            """
select (t.created_at at time zone 'UTC')::date as day,count(*)
  from task t where t.project_id=? and t.trashed_at is null and t.created_at>=? and t.created_at<?
  group by day
""",
            projectId,
            start,
            end);
    var done =
        counts(
            """
            select (h.changed_at at time zone 'UTC')::date as day,count(distinct h.task_id)
            from task_status_history h join task t on t.id=h.task_id and t.project_id=h.project_id
            where h.project_id=? and h.changed_at>=? and h.changed_at<? and h.status='Done'
              and t.trashed_at is null and (
                select p.status from task_status_history p
                where p.task_id=h.task_id and p.project_id=h.project_id
                  and (p.changed_at,p.id)<(h.changed_at,h.id)
                order by p.changed_at desc,p.id desc limit 1
              ) is distinct from 'Done'
            group by day
            """,
            projectId,
            start,
            end);
    return new FlowSignals(
        start.plusDays(EwmaControlChart.BASELINE_PERIODS),
        chart(incoming, start),
        chart(done, start));
  }

  private Map<LocalDate, Integer> counts(
      String query, long project, LocalDate start, LocalDate end) {
    return jdbc.query(
        query,
        result -> {
          var counts = new HashMap<LocalDate, Integer>();
          while (result.next()) counts.put(result.getObject(1, LocalDate.class), result.getInt(2));
          return counts;
        },
        project,
        timestamp(start),
        timestamp(end));
  }

  private static Chart chart(Map<LocalDate, Integer> counts, LocalDate start) {
    List<Integer> periods =
        IntStream.range(0, EwmaControlChart.BASELINE_PERIODS + EwmaControlChart.MONITOR_PERIODS)
            .mapToObj(day -> counts.getOrDefault(start.plusDays(day), 0))
            .toList();
    return EwmaControlChart.compare(
        periods.subList(0, EwmaControlChart.BASELINE_PERIODS),
        periods.subList(EwmaControlChart.BASELINE_PERIODS, periods.size()));
  }

  private void recordSignal(long projectId, LocalDate day, Metric metric, Chart chart, Instant at) {
    Point point = chart.monitoring().getLast();
    if (point.signal() == Signal.IN_LIMITS) return;
    int inserted =
        jdbc.update(
            """
insert into project_flow_anomaly(project_id,period,metric,direction,observed,weighted,baseline,lower_limit,upper_limit)
values(?,?,?,?,?,?,?,?,?) on conflict(project_id,period,metric) do nothing
""",
            projectId,
            day,
            metric.name(),
            point.signal().name(),
            point.observed(),
            point.weighted(),
            chart.baselineMean(),
            point.lower(),
            point.upper());
    if (inserted == 0) return;
    Boolean ongoing =
        jdbc.queryForObject(
            "select exists(select 1 from project_flow_anomaly where project_id=? and period=? and"
                + " metric=? and direction=?)",
            Boolean.class,
            projectId,
            day.minusDays(1),
            metric.name(),
            point.signal().name());
    if (Boolean.TRUE.equals(ongoing)) return;
    String message =
        switch (metric) {
          case INFLOW ->
              point.signal() == Signal.HIGH
                  ? "Task inflow is above its flow control limit."
                  : "Task inflow is below its flow control limit.";
          case THROUGHPUT ->
              point.signal() == Signal.HIGH
                  ? "Task throughput is above its flow control limit."
                  : "Task throughput is below its flow control limit.";
        };
    notifications.publishEvent(
        new NotificationRequested(UUID.randomUUID(), projectId, null, at, null, message, true));
  }

  private static Timestamp timestamp(LocalDate day) {
    return Timestamp.from(day.atStartOfDay().toInstant(ZoneOffset.UTC));
  }
}

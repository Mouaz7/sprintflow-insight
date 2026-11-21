package com.sprintflowinsight.risk.live;

import com.sprintflowinsight.risk.RiskFeatures;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RiskFeatureReader {
  private final JdbcTemplate jdbc;

  RiskFeatureReader(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  record Snapshot(long taskId, RiskFeatures features) {}

  record Dependency(long predecessor, long successor) {}

  List<Snapshot> read(long projectId, long sprintId, Instant now, long remainingDays) {
    var edges =
        jdbc.query(
            """
            select d.blocker_task_id, d.blocked_task_id
            from task_dependency d
              join task a on a.id=d.blocker_task_id and a.project_id=d.project_id
              join task b on b.id=d.blocked_task_id and b.project_id=d.project_id
            where d.project_id=? and a.trashed_at is null and b.trashed_at is null
              and a.status<>'Done' and b.status<>'Done'
            """,
            (row, index) -> new Dependency(row.getLong(1), row.getLong(2)),
            projectId);
    Map<Long, Integer> depths = dependencyDepths(edges);
    return jdbc.query(
        """
with sprint_tasks as materialized (
  select id, status, story_points, created_at, risk_block_count, risk_reassignment_count,
    count(*) over (partition by status) as column_wip
  from task where project_id=? and sprint_id=? and trashed_at is null and status<>'Done'
), changes as (
  select h.task_id, h.status, h.changed_at, h.id,
    lag(h.status) over (partition by h.task_id order by h.changed_at,h.id) as previous
  from task_status_history h join task t on t.id=h.task_id and t.project_id=h.project_id
  where h.project_id=? and h.changed_at<=? and t.trashed_at is null
), spans as (
  select task_id,status,changed_at,
    lead(changed_at) over (partition by task_id order by changed_at,id) as left_at
  from changes where status is distinct from previous
), reference as (
  select status, greatest(1, percentile_cont(0.85) within group
    (order by extract(epoch from left_at-changed_at)/3600)) as hours
  from spans where left_at>=? group by status
), comments as (
  select c.task_id,count(*) as total from task_comment c join sprint_tasks t on t.id=c.task_id
  where c.project_id=? and c.created_at>? and c.created_at<=? group by c.task_id
)
select t.id, case when r.hours is null then 1.0 else greatest(0,
    extract(epoch from cast(? as timestamptz)-coalesce(s.changed_at,t.created_at))/3600/r.hours)
  end as age_ratio, t.risk_block_count, t.risk_reassignment_count,
  coalesce(c.total,0) as comment_count,t.story_points,t.column_wip
from sprint_tasks t
  left join spans s on s.task_id=t.id and s.left_at is null and s.status=t.status
  left join reference r on r.status=t.status
  left join comments c on c.task_id=t.id
order by t.id
""",
        (row, index) ->
            new Snapshot(
                row.getLong(1),
                new RiskFeatures(
                    row.getDouble(2),
                    row.getInt(3),
                    row.getInt(4),
                    row.getInt(5),
                    row.getInt(6),
                    depths.getOrDefault(row.getLong(1), 0),
                    remainingDays,
                    row.getInt(7))),
        projectId,
        sprintId,
        projectId,
        Timestamp.from(now),
        Timestamp.from(now.minus(180, ChronoUnit.DAYS)),
        projectId,
        Timestamp.from(now.minus(7, ChronoUnit.DAYS)),
        Timestamp.from(now),
        Timestamp.from(now));
  }

  static Map<Long, Integer> dependencyDepths(List<Dependency> edges) {
    var successors = new HashMap<Long, List<Long>>();
    var incoming = new HashMap<Long, Integer>();
    for (Dependency edge : edges) {
      successors
          .computeIfAbsent(edge.predecessor(), ignored -> new ArrayList<>())
          .add(edge.successor());
      incoming.putIfAbsent(edge.predecessor(), 0);
      incoming.merge(edge.successor(), 1, Integer::sum);
    }
    var ready = new ArrayDeque<Long>();
    incoming.forEach(
        (task, count) -> {
          if (count == 0) ready.add(task);
        });
    var depths = new HashMap<Long, Integer>();
    while (!ready.isEmpty()) {
      long task = ready.removeFirst();
      int depth = depths.getOrDefault(task, 0);
      for (long successor : successors.getOrDefault(task, List.of())) {
        depths.merge(successor, depth + 1, Math::max);
        if (incoming.merge(successor, -1, Integer::sum) == 0) ready.add(successor);
      }
    }
    return depths;
  }
}

package com.sprintflowinsight.realtime;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "app.realtime.relay.enabled", havingValue = "true")
class ClusterPresence {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;
  private final long ttlMillis;

  ClusterPresence(
      JdbcTemplate jdbc,
      PlatformTransactionManager manager,
      @Value("${app.realtime.presence-ttl:PT90S}") Duration ttl) {
    this.jdbc = jdbc;
    this.transaction = new TransactionTemplate(manager);
    this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.ttlMillis = ttl.toMillis();
  }

  /** Take the local snapshot and commit under one lock, including concurrent disconnects. */
  synchronized Set<Long> sync(String socket, PresenceRegistry local) {
    var views = local.views(socket);
    return transaction.execute(
        status -> {
          Set<Long> changed =
              new HashSet<>(
                  jdbc.queryForList(
                      "delete from realtime_presence where socket_id=? returning project_id",
                      Long.class,
                      socket));
          for (var view : views) {
            jdbc.update(
                """
                insert into realtime_presence(socket_id, project_id, user_id, display_name,
                                              task_id, expires_at)
                values (?, ?, ?, ?, ?, clock_timestamp() + ? * interval '1 millisecond')
                """,
                socket,
                view.projectId(),
                view.userId(),
                view.name(),
                view.taskId(),
                ttlMillis);
            changed.add(view.projectId());
          }
          return Set.copyOf(changed);
        });
  }

  void touch(String socket) {
    jdbc.update(
        """
        update realtime_presence set expires_at=clock_timestamp() + ? * interval '1 millisecond'
        where socket_id=?
        """,
        ttlMillis,
        socket);
  }

  Set<Long> expire() {
    return new HashSet<>(
        jdbc.queryForList(
            "delete from realtime_presence where expires_at<=clock_timestamp() returning"
                + " project_id",
            Long.class));
  }

  List<PresenceMessage.Viewer> viewers(long projectId) {
    var viewers = new HashMap<Long, String>();
    var taskIds = new HashMap<Long, TreeSet<Long>>();
    jdbc.query(
        """
        select user_id, display_name, task_id from realtime_presence
        where project_id=? and expires_at>clock_timestamp()
        """,
        result -> {
          long userId = result.getLong("user_id");
          viewers.putIfAbsent(userId, result.getString("display_name"));
          var tasks = taskIds.computeIfAbsent(userId, ignored -> new TreeSet<>());
          Long taskId = result.getObject("task_id", Long.class);
          if (taskId != null) tasks.add(taskId);
        },
        projectId);
    return viewers.entrySet().stream()
        .map(
            viewer ->
                new PresenceMessage.Viewer(
                    viewer.getKey(), viewer.getValue(), List.copyOf(taskIds.get(viewer.getKey()))))
        .sorted(
            Comparator.comparing(PresenceMessage.Viewer::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(PresenceMessage.Viewer::userId))
        .toList();
  }
}

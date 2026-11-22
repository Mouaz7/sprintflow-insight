package com.sprintflowinsight.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprintflowinsight.support.MigrationFixture;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

class ClusterPresenceIntegrationTest {
  @Test
  void combinesViewersAcrossReplicasAndExpiresAReplicaThatStopsRenewing() throws Exception {
    try (var fixture = new MigrationFixture()) {
      fixture.migrateTo("38");
      var source = fixture.dataSource();
      var jdbc = new JdbcTemplate(source);
      long ada =
          jdbc.queryForObject(
              "insert into users(email) values ('ada@example.test') returning id", Long.class);
      long grace =
          jdbc.queryForObject(
              "insert into users(email) values ('grace@example.test') returning id", Long.class);
      long project =
          jdbc.queryForObject(
              "insert into projects(name, owner_email, key) values ('Live', 'ada@example.test',"
                  + " 'LIVE') returning id",
              Long.class);
      var ttl = Duration.ofSeconds(90);
      var first = new ClusterPresence(jdbc, new DataSourceTransactionManager(source), ttl);
      var second =
          new ClusterPresence(
              new JdbcTemplate(source), new DataSourceTransactionManager(source), ttl);
      var localFirst = new PresenceRegistry(ttl);
      var localSecond = new PresenceRegistry(ttl);
      var now = Instant.parse("2026-11-21T18:00:00Z");
      localFirst.subscribed("a", "events", project, true, ada, "Ada", now);
      localFirst.openTask("a", project, 17L, now);
      localSecond.subscribed("b", "events", project, true, ada, "Ada", now);
      localSecond.openTask("b", project, 18L, now);
      localSecond.subscribed("c", "events", project, true, grace, "Grace", now);

      assertThat(first.sync("a", localFirst)).containsExactly(project);
      second.sync("b", localSecond);
      second.sync("c", localSecond);
      assertThat(first.viewers(project))
          .containsExactly(
              new PresenceMessage.Viewer(ada, "Ada", List.of(17L, 18L)),
              new PresenceMessage.Viewer(grace, "Grace"));

      jdbc.update(
          "update realtime_presence set expires_at=clock_timestamp()-interval '1 second' where"
              + " socket_id in ('a', 'b')");
      second.touch("b");
      assertThat(second.expire()).containsExactly(project);
      assertThat(first.viewers(project).getFirst().taskIds()).containsExactly(18L);
      localSecond.disconnected("b");
      second.sync("b", localSecond);
      assertThat(first.viewers(project))
          .containsExactly(new PresenceMessage.Viewer(grace, "Grace"));
    }
  }
}

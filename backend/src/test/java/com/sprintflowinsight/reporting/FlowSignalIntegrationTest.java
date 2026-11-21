package com.sprintflowinsight.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprintflowinsight.support.ApiIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FlowSignalIntegrationTest extends ApiIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  @Autowired FlowSignalService service;

  @Test
  void sendsOneOwnerNotificationPerDirectionAndIgnoresRepeatedDoneSnapshots() throws Exception {
    var owner = user();
    long projectId = project(owner, null).getId();
    var now = Instant.now();
    var today = LocalDate.ofInstant(now, ZoneOffset.UTC);
    var yesterday = Timestamp.from(today.minusDays(1).atTime(9, 0).toInstant(ZoneOffset.UTC));
    var old = Timestamp.from(today.minusDays(36).atStartOfDay().toInstant(ZoneOffset.UTC));
    jdbc.update(
        "insert into"
            + " task(project_id,title,status,is_blocked,number,story_points,version,created_at)"
            + " values(?,'Older task','To Do',false,1,0,0,?)",
        projectId,
        old);
    jdbc.update(
        """
with created as (
  insert into task(project_id,title,status,is_blocked,number,story_points,version,created_at)
  select ?,'Burst','Done',false,n,0,0,? from generate_series(2,21) n returning id,project_id,created_at
)
insert into task_status_history(task_id,project_id,changed_at,status)
select id,project_id,created_at+offsets.elapsed,'Done'
from created cross join (values (interval '1 hour'), (interval '2 hours')) offsets(elapsed)
""",
        projectId,
        yesterday);
    String endpoint = "/api/projects/" + projectId + "/flow-signals";
    mvc.perform(get(endpoint).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.inflow.monitoring[6].observed").value(20))
        .andExpect(jsonPath("$.throughput.monitoring[6].observed").value(20))
        .andExpect(jsonPath("$.throughput.monitoring[6].signal").value("HIGH"));
    service.checkAndNotify(projectId, now);
    service.checkAndNotify(projectId, now);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from project_flow_anomaly where project_id=?",
                Integer.class,
                projectId))
        .isEqualTo(2);
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(
                        jdbc.queryForList(
                            "select message from notification where project_id=? and"
                                + " recipient_email=?",
                            String.class,
                            projectId,
                            owner.getEmail()))
                    .containsExactlyInAnyOrder(
                        "Task throughput is above its flow control limit.",
                        "Task inflow is above its flow control limit."));
  }

  @Test
  void allowsCurrentMembersAndRequiresCompleteHistory() throws Exception {
    var owner = user();
    var project = project(owner, null);
    String endpoint = "/api/projects/" + project.getId() + "/flow-signals";
    for (String role : List.of("Owner", "Admin", "Member", "Viewer")) {
      var caller = role.equals("Owner") ? owner : user();
      if (!role.equals("Owner")) member(project, caller, role);
      mvc.perform(get(endpoint).with(as(caller)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.firstMonitoredDay").isEmpty());
    }
    mvc.perform(get(endpoint).with(as(user()))).andExpect(status().isForbidden());
  }
}

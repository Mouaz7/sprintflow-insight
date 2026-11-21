package com.sprintflowinsight.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprintflowinsight.support.ApiIntegrationTest;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

@TestPropertySource(properties = "app.webhooks.dispatch.delay=PT24H")
class OutboundWebhookIntegrationTest extends ApiIntegrationTest {
  private static final String DRAFT =
      """
      {"url":"https://hooks.example.test/one","events":["task.created"]}
      """;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired OutboundWebhookDispatcher dispatcher;
  @MockitoBean OutboundWebhookTransport transport;

  @Test
  void persistsSubscribedEventsAndRetriesWithTheSameDeliveryId() throws Exception {
    var owner = user();
    var project = project(owner, null);
    String base = "/api/projects/" + project.getId() + "/webhooks";
    var created =
        mvc.perform(
                post(base)
                    .with(as(owner))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(DRAFT))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse();
    assertThat(created.getHeader("Cache-Control")).isEqualTo("no-store");
    var webhook = json.readTree(created.getContentAsString());
    long id = webhook.path("webhook").path("id").asLong();
    assertThat(id).isPositive();
    assertThat(
            jdbc.queryForObject(
                "select secret_ciphertext from outbound_webhook where id=?", String.class, id))
        .isNotEqualTo(webhook.path("secret").asText());
    mvc.perform(get(base).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].secret").doesNotExist());

    when(transport.send(
            any(URI.class), anyString(), any(UUID.class), eq("task.created"), anyString()))
        .thenReturn(
            OutboundWebhookTransport.Result.status(503),
            OutboundWebhookTransport.Result.status(204));
    mvc.perform(
            post("/api/tasks")
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "projectId",
                            project.getId(),
                            "title",
                            "Private task title",
                            "description",
                            "Private task description",
                            "status",
                            "To Do"))))
        .andExpect(status().isOk());
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(
                        jdbc.queryForObject(
                            "select count(*) from outbound_webhook_delivery where webhook_id=?",
                            Long.class,
                            id))
                    .isEqualTo(1));
    dispatcher.dispatch();
    String history = base + "/" + id + "/deliveries";
    mvc.perform(get(history).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].outcome").value("PENDING"))
        .andExpect(jsonPath("$[0].lastHttpStatus").value(503));
    jdbc.update(
        "update outbound_webhook_delivery set next_attempt_at=clock_timestamp() where webhook_id=?",
        id);
    dispatcher.dispatch();
    mvc.perform(get(history).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].outcome").value("DELIVERED"))
        .andExpect(jsonPath("$[0].attempts").value(2));
    var deliveryIds = ArgumentCaptor.forClass(UUID.class);
    var payloads = ArgumentCaptor.forClass(String.class);
    verify(transport, times(2))
        .send(
            any(URI.class),
            eq(webhook.path("secret").asText()),
            deliveryIds.capture(),
            eq("task.created"),
            payloads.capture());
    assertThat(deliveryIds.getAllValues()).containsOnly(deliveryIds.getValue());
    assertThat(payloads.getAllValues()).containsOnly(payloads.getValue());
    assertThat(payloads.getValue()).doesNotContain("Private task", owner.getEmail());
    mvc.perform(delete(base + "/" + id).with(as(owner)).with(csrf()))
        .andExpect(status().isNoContent());
  }

  @Test
  void managesWebhooksOnlyWithAProjectRoleAndPreventsArchivedWrites() throws Exception {
    var owner = user();
    var project = project(owner, null);
    String base = "/api/projects/" + project.getId() + "/webhooks";
    for (String role : List.of("Owner", "Admin", "Member", "Viewer", "Outsider")) {
      var caller = role.equals("Owner") ? owner : user();
      if (List.of("Admin", "Member", "Viewer").contains(role)) member(project, caller, role);
      boolean allowed = List.of("Owner", "Admin").contains(role);
      mvc.perform(get(base).with(as(caller))).andExpect(status().is(allowed ? 200 : 403));
      mvc.perform(
              post(base)
                  .with(as(caller))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(DRAFT))
          .andExpect(status().is(allowed ? 201 : 403));
    }
    jdbc.update("update projects set archived_at=now() where id=?", project.getId());
    mvc.perform(get(base).with(as(owner))).andExpect(status().isOk());
    mvc.perform(
            post(base)
                .with(as(owner))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(DRAFT))
        .andExpect(status().isConflict());
  }
}

package com.sprintflowinsight.integration;

import com.sprintflowinsight.common.SecureTokens;
import com.sprintflowinsight.common.crypto.EncryptedColumn;
import com.sprintflowinsight.common.crypto.EncryptionService;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.project.AccessContext.Need;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.project.ProjectMutationService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.net.UnknownHostException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OutboundWebhooks {
  static final EncryptedColumn SECRET =
      EncryptedColumn.of("outbound_webhook", "id", "secret_ciphertext");
  private static final String METADATA =
      "id,target_url,array_to_string(event_types,','),created_at";

  record Draft(
      @NotBlank @Size(max = 1024) String url,
      @NotEmpty @Size(max = 4)
          Set<@NotNull @Pattern(regexp = "task\\.(created|updated|moved|deleted)") String>
              events) {}

  record Webhook(long id, String url, List<String> events, Instant createdAt) {}

  record Issued(Webhook webhook, String secret) {
    @Override
    public String toString() {
      return "IssuedWebhook[secret=redacted]";
    }
  }

  record Delivery(
      UUID id,
      UUID eventId,
      String event,
      String outcome,
      int attempts,
      Instant createdAt,
      Instant nextAttemptAt,
      Integer lastHttpStatus,
      String lastResult) {}

  private final ProjectAccessService access;
  private final ProjectMutationService mutations;
  private final JdbcTemplate jdbc;
  private final EncryptionService encryption;
  private final OutboundWebhookTransport transport;

  OutboundWebhooks(
      ProjectAccessService access,
      ProjectMutationService mutations,
      JdbcTemplate jdbc,
      EncryptionService encryption,
      OutboundWebhookTransport transport) {
    this.access = access;
    this.mutations = mutations;
    this.jdbc = jdbc;
    this.encryption = encryption;
    this.transport = transport;
  }

  @Bean
  EncryptedColumn outboundWebhookSecret() {
    return SECRET;
  }

  @Transactional(readOnly = true)
  public List<Webhook> list(long projectId, HttpSession session) {
    access.require(projectId, session, Need.MANAGE_ARCHIVED);
    return jdbc.query(
        "select " + METADATA + " from outbound_webhook where project_id=? order by id desc",
        OutboundWebhooks::metadata,
        projectId);
  }

  @Transactional
  public Issued create(long projectId, Draft draft, HttpSession session) {
    access.require(projectId, session, Need.MANAGE);
    URI uri;
    try {
      uri = PublicWebhookDestination.https(draft.url().strip());
      if (uri.toASCIIString().length() > 1024) {
        throw new IllegalArgumentException("Webhook URL exceeds 1024 characters in ASCII form.");
      }
      transport.verifyDestination(uri);
    } catch (IllegalArgumentException | UnknownHostException invalid) {
      throw ApiException.badRequest("Use a publicly reachable HTTPS webhook URL.");
    }
    mutations.lock(projectId);
    Integer count =
        jdbc.queryForObject(
            "select count(*) from outbound_webhook where project_id=?", Integer.class, projectId);
    if (count != null && count >= 10)
      throw ApiException.conflict("Remove a webhook before adding another.");
    String secret = SecureTokens.token();
    var webhook =
        jdbc.queryForObject(
            """
            insert into outbound_webhook(project_id,target_url,secret_ciphertext,event_types)
            values(?,?,?,string_to_array(?,',')) returning
            """
                + METADATA,
            OutboundWebhooks::metadata,
            projectId,
            uri.toASCIIString(),
            encryption.encrypt(secret, SECRET.context()),
            String.join(",", draft.events().stream().sorted().toList()));
    return new Issued(webhook, secret);
  }

  @Transactional
  public void remove(long projectId, long webhookId, HttpSession session) {
    access.require(projectId, session, Need.MANAGE);
    mutations.lock(projectId);
    if (jdbc.update(
            "delete from outbound_webhook where id=? and project_id=?", webhookId, projectId)
        == 0) throw ApiException.notFound("Webhook not found.");
  }

  @Transactional(readOnly = true)
  public List<Delivery> deliveries(long projectId, long webhookId, HttpSession session) {
    access.require(projectId, session, Need.MANAGE_ARCHIVED);
    if (!Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            select exists(select 1 from outbound_webhook where id=? and project_id=?)
            """,
            Boolean.class,
            webhookId,
            projectId))) throw ApiException.notFound("Webhook not found.");
    return jdbc.query(
        """
        select d.id,d.event_id,d.event_type,d.outcome,d.attempts,d.created_at,
          case when d.outcome='PENDING' then d.next_attempt_at end, a.status,a.result
        from outbound_webhook_delivery d left join outbound_webhook_attempt a
          on a.delivery_id=d.id and a.attempt=d.attempts
        where d.webhook_id=? order by d.created_at desc,d.id limit 100
        """,
        (row, index) -> {
          var next = row.getTimestamp(7);
          return new Delivery(
              row.getObject(1, UUID.class),
              row.getObject(2, UUID.class),
              row.getString(3),
              row.getString(4),
              row.getInt(5),
              row.getTimestamp(6).toInstant(),
              next == null ? null : next.toInstant(),
              row.getObject(8, Integer.class),
              row.getString(9));
        },
        webhookId);
  }

  private static Webhook metadata(ResultSet row, int index) throws SQLException {
    return new Webhook(
        row.getLong(1),
        row.getString(2),
        Arrays.asList(row.getString(3).split(",")),
        row.getTimestamp(4).toInstant());
  }
}

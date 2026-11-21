package com.sprintflowinsight.automation;

import com.sprintflowinsight.common.crypto.EncryptedColumn;
import com.sprintflowinsight.common.crypto.EncryptionService;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.project.AccessContext.Need;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.task.TaskService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
class GithubAutomation {
  static final int MAX_PAYLOAD_BYTES = 1_048_576;
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final java.util.regex.Pattern TASK_KEY =
      java.util.regex.Pattern.compile(
          "(?i)(?<![a-z0-9])([a-z]{2,10})-([1-9][0-9]{0,9})(?![a-z0-9])");
  private static final EncryptedColumn SECRET =
      EncryptedColumn.of("automation_github", "project_id", "encrypted_secret");

  private final AutomationRules rules;
  private final ProjectAccessService access;
  private final EncryptionService encryption;
  private final ApplicationEventPublisher events;
  private final TaskService tasks;
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  GithubAutomation(
      AutomationRules rules,
      ProjectAccessService access,
      EncryptionService encryption,
      ApplicationEventPublisher events,
      TaskService tasks,
      JdbcTemplate jdbc,
      ObjectMapper json) {
    this.rules = rules;
    this.access = access;
    this.encryption = encryption;
    this.events = events;
    this.tasks = tasks;
    this.jdbc = jdbc;
    this.json = json;
  }

  @Bean
  EncryptedColumn automationGithubSecret() {
    return SECRET;
  }

  record Draft(
      @NotBlank @Size(max = 180) @Pattern(regexp = "[A-Za-z0-9-]+/[A-Za-z0-9._-]+")
          String repository) {}

  record Rules(@NotNull Boolean reviewOnOpen, @NotNull Boolean doneOnMerge) {}

  record Settings(
      UUID endpointId,
      String repository,
      Instant createdAt,
      boolean reviewOnOpen,
      boolean doneOnMerge) {}

  record TaskLink(
      String kind,
      String repository,
      String key,
      String url,
      String title,
      String state,
      Instant linkedAt) {}

  record DeliveryLog(
      UUID id,
      String repository,
      String event,
      String action,
      int linkedTasks,
      int movedTasks,
      Instant receivedAt) {}

  record Issued(Settings settings, String secret) {
    @Override
    public String toString() {
      return "Issued[settings=" + settings + ", secret=redacted]";
    }
  }

  record Link(
      long projectId,
      String projectKey,
      String repository,
      String encryptedSecret,
      boolean active,
      boolean reviewOnOpen,
      boolean doneOnMerge) {}

  @Transactional(readOnly = true)
  public Optional<Settings> settings(long projectId, HttpSession session) {
    access.require(projectId, session, Need.MANAGE_ARCHIVED);
    return jdbc
        .query(
            "select endpoint_id,repository,created_at,review_on_open,done_on_merge"
                + " from automation_github where project_id=?",
            (row, index) ->
                new Settings(
                    row.getObject(1, UUID.class),
                    row.getString(2),
                    row.getTimestamp(3).toInstant(),
                    row.getBoolean(4),
                    row.getBoolean(5)),
            projectId)
        .stream()
        .findFirst();
  }

  @Transactional
  public Issued configure(long projectId, Draft draft, HttpSession session) {
    rules.manageLock(projectId, session);
    var bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    var settings =
        jdbc.queryForObject(
            """
insert into automation_github(project_id,endpoint_id,repository,encrypted_secret)
values(?,?,?,?) on conflict(project_id) do update set endpoint_id=excluded.endpoint_id,
  repository=excluded.repository,encrypted_secret=excluded.encrypted_secret,created_at=now()
returning endpoint_id,repository,created_at,review_on_open,done_on_merge
""",
            (row, index) ->
                new Settings(
                    row.getObject(1, UUID.class),
                    row.getString(2),
                    row.getTimestamp(3).toInstant(),
                    row.getBoolean(4),
                    row.getBoolean(5)),
            projectId,
            UUID.randomUUID(),
            draft.repository().toLowerCase(Locale.ROOT),
            encryption.encrypt(secret, SECRET.context()));
    return new Issued(settings, secret);
  }

  @Transactional
  public void disconnect(long projectId, HttpSession session) {
    rules.manageLock(projectId, session);
    jdbc.update("delete from automation_github where project_id=?", projectId);
  }

  @Transactional
  public void updateRules(long projectId, Rules draft, HttpSession session) {
    rules.manageLock(projectId, session);
    if (jdbc.update(
            "update automation_github set review_on_open=?,done_on_merge=? where project_id=?",
            draft.reviewOnOpen(),
            draft.doneOnMerge(),
            projectId)
        == 0) {
      throw ApiException.notFound("Connect a GitHub repository first.");
    }
  }

  @Transactional(readOnly = true)
  public List<DeliveryLog> history(long projectId, HttpSession session) {
    access.require(projectId, session, Need.MANAGE_ARCHIVED);
    return jdbc.query(
        """
        select delivery_id,repository,event_type,event_action,linked_tasks,moved_tasks,received_at
        from github_delivery where project_id=? order by received_at desc,delivery_id limit 100
        """,
        (row, index) ->
            new DeliveryLog(
                row.getObject(1, UUID.class),
                row.getString(2),
                row.getString(3),
                row.getString(4),
                row.getInt(5),
                row.getInt(6),
                row.getTimestamp(7).toInstant()),
        projectId);
  }

  @Transactional(readOnly = true)
  public List<TaskLink> taskLinks(long projectId, long taskId, HttpSession session) {
    access.require(projectId, session, Need.READ);
    tasks
        .gettaskById(taskId)
        .filter(task -> task.getProjectId() == projectId)
        .orElseThrow(() -> ApiException.notFound("Task not found."));
    return jdbc.query(
        """
        select kind,repository,remote_key,title,state,linked_at from task_link where task_id=?
        order by linked_at desc,repository,kind,remote_key limit 100
        """,
        (row, index) -> {
          String kind = row.getString(1);
          String repository = row.getString(2);
          String key = row.getString(3);
          String url =
              "https://github.com/"
                  + repository
                  + ("PULL_REQUEST".equals(kind) ? "/pull/" : "/commit/")
                  + key;
          return new TaskLink(
              kind,
              repository,
              key,
              url,
              row.getString(4),
              row.getString(5),
              row.getTimestamp(6).toInstant());
        },
        taskId);
  }

  @Transactional
  public void receive(
      UUID endpointId, String eventType, String deliveryId, String signature, byte[] raw) {
    if (signature == null || signature.length() != 71 || !signature.startsWith("sha256="))
      throw unauthorized();
    Link link = find(endpointId);
    if (!signatureMatches(
        encryption.decrypt(link.encryptedSecret(), SECRET.context()), raw, signature))
      throw unauthorized();
    if (!link.active() || !List.of("pull_request", "push").contains(eventType)) return;

    JsonNode message;
    try {
      message = json.readTree(raw);
    } catch (JacksonException error) {
      throw ApiException.badRequest("GitHub webhook body must be JSON.");
    }
    if (message == null || !message.isObject())
      throw ApiException.badRequest("GitHub webhook body must be JSON.");
    if (!link.repository().equalsIgnoreCase(message.path("repository").path("full_name").asText()))
      throw unauthorized();
    UUID deliveryKey = deliveryKey(deliveryId);
    var delivery =
        "pull_request".equals(eventType)
            ? GithubWebhook.pullRequest(message)
            : GithubWebhook.push(message);
    if (delivery.references().isEmpty() && "pull_request".equals(eventType)) return;
    // The project row is also the ordinary write lock used by edits, archive and configuration.
    if (jdbc.queryForList(
            "select id from projects where id=? for update", Long.class, link.projectId())
        .isEmpty()) return;
    Link current = find(endpointId);
    if (!current.active()) return;
    if (jdbc.update(
            """
insert into github_delivery(project_id,delivery_id,repository,body_hash,event_type,event_action)
values(?,?,?,sha256(?),?,?) on conflict do nothing
""",
            current.projectId(),
            deliveryKey,
            current.repository(),
            raw,
            eventType,
            delivery.action())
        == 0) return;
    applyDelivery(current, deliveryKey, delivery);
  }

  private void applyDelivery(Link link, UUID deliveryKey, GithubWebhook.Delivery delivery) {
    var linked = new LinkedHashSet<Long>();
    int moved = 0;
    for (var reference : delivery.references()) {
      for (int number : taskNumbers(reference.text(), link.projectKey())) {
        var ids =
            jdbc.queryForList(
                "select id from task where project_id=? and number=? and trashed_at is null",
                Long.class,
                link.projectId(),
                number);
        for (long taskId : ids) {
          if (linkTask(link, taskId, reference)) {
            linked.add(taskId);
            if (moveTask(link, taskId, delivery.action(), reference.state())) moved++;
          }
          if ("closed".equals(delivery.action()) && "MERGED".equals(reference.state())) {
            publishMerged(link.projectId(), taskId, delivery.pullRequestId());
          }
        }
      }
    }
    jdbc.update(
        "update github_delivery set linked_tasks=?,moved_tasks=? where project_id=? and"
            + " delivery_id=?",
        linked.size(),
        moved,
        link.projectId(),
        deliveryKey);
  }

  private boolean linkTask(Link link, long taskId, GithubWebhook.Reference reference) {
    return jdbc.update(
            """
insert into task_link(task_id,repository,kind,remote_key,title,state) values(?,?,?,?,?,?)
on conflict(task_id,repository,kind,remote_key) do update set title=excluded.title,state=excluded.state
where task_link.kind='PULL_REQUEST'
  and (task_link.state<>'MERGED' or excluded.state='MERGED')
  and (task_link.title,task_link.state) is distinct from (excluded.title,excluded.state)
""",
            taskId,
            link.repository(),
            reference.kind(),
            reference.key(),
            reference.title(),
            reference.state())
        == 1;
  }

  private boolean moveTask(Link link, long taskId, String action, String state) {
    String target =
        "opened".equals(action) && link.reviewOnOpen()
            ? "Review"
            : "closed".equals(action) && "MERGED".equals(state) && link.doneOnMerge()
                ? "Done"
                : null;
    if (target == null) return false;
    var task = tasks.gettaskById(taskId).orElseThrow();
    if ("Done".equals(task.getStatus()) || AutomationWorker.moveBlocked(task, target) != null)
      return false;
    var updated = AutomationWorker.copy(task);
    updated.setStatus(target);
    tasks.updateTask(task, updated, "automation@sprintflow.invalid");
    return true;
  }

  private void publishMerged(long projectId, long taskId, long prId) {
    UUID signalId =
        UUID.nameUUIDFromBytes(
            ("github:" + projectId + ":" + prId).getBytes(StandardCharsets.UTF_8));
    events.publishEvent(
        new AutomationSignal(
            signalId,
            projectId,
            taskId,
            AutomationRule.TriggerKind.PULL_REQUEST_MERGED,
            null,
            null,
            null,
            List.of()));
  }

  private Link find(UUID endpointId) {
    return jdbc
        .query(
            """
select p.id,p.key,g.repository,g.encrypted_secret,p.trashed_at is null and p.archived_at is null,
  g.review_on_open,g.done_on_merge
from automation_github g join projects p on p.id=g.project_id where g.endpoint_id=?
""",
            (row, index) ->
                new Link(
                    row.getLong(1),
                    row.getString(2),
                    row.getString(3),
                    row.getString(4),
                    row.getBoolean(5),
                    row.getBoolean(6),
                    row.getBoolean(7)),
            endpointId)
        .stream()
        .findFirst()
        .orElseThrow(GithubAutomation::unauthorized);
  }

  private static UUID deliveryKey(String value) {
    if (value == null || value.length() != 36)
      throw ApiException.badRequest("X-GitHub-Delivery must be a UUID.");
    try {
      UUID id = UUID.fromString(value);
      if (id.toString().equalsIgnoreCase(value)) return id;
    } catch (IllegalArgumentException invalid) {
      throw ApiException.badRequest("X-GitHub-Delivery must be a UUID.");
    }
    throw ApiException.badRequest("X-GitHub-Delivery must be a UUID.");
  }

  static List<Integer> taskNumbers(String reference, String projectKey) {
    var result = new LinkedHashSet<Integer>();
    var matches = TASK_KEY.matcher(reference);
    while (result.size() < 25 && matches.find()) {
      if (!projectKey.equalsIgnoreCase(matches.group(1))) continue;
      long number = Long.parseLong(matches.group(2));
      if (number <= Integer.MAX_VALUE) result.add((int) number);
    }
    return List.copyOf(result);
  }

  static boolean signatureMatches(String key, byte[] raw, String signature) {
    byte[] expected;
    try {
      expected = HexFormat.of().parseHex(signature, "sha256=".length(), signature.length());
    } catch (IllegalArgumentException malformed) {
      return false;
    }
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return MessageDigest.isEqual(mac.doFinal(raw), expected);
    } catch (GeneralSecurityException error) {
      throw new IllegalStateException("Cannot verify the GitHub webhook signature.", error);
    }
  }

  private static ResponseStatusException unauthorized() {
    return new ResponseStatusException(
        HttpStatus.UNAUTHORIZED, "Invalid GitHub webhook signature.");
  }
}

package com.sprintflowinsight.automation;

import com.sprintflowinsight.common.crypto.EncryptedColumn;
import com.sprintflowinsight.common.crypto.EncryptionService;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.project.AccessContext.Need;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
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
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  GithubAutomation(
      AutomationRules rules,
      ProjectAccessService access,
      EncryptionService encryption,
      ApplicationEventPublisher events,
      JdbcTemplate jdbc,
      ObjectMapper json) {
    this.rules = rules;
    this.access = access;
    this.encryption = encryption;
    this.events = events;
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

  record Settings(UUID endpointId, String repository, Instant createdAt) {}

  record Issued(Settings settings, String secret) {}

  record Link(
      long projectId,
      String projectKey,
      String repository,
      String encryptedSecret,
      boolean active) {}

  @Transactional(readOnly = true)
  public Optional<Settings> settings(long projectId, HttpSession session) {
    access.require(projectId, session, Need.MANAGE_ARCHIVED);
    return jdbc
        .query(
            "select endpoint_id,repository,created_at from automation_github where project_id=?",
            (row, index) ->
                new Settings(
                    row.getObject(1, UUID.class),
                    row.getString(2),
                    row.getTimestamp(3).toInstant()),
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
returning endpoint_id,repository,created_at
""",
            (row, index) ->
                new Settings(
                    row.getObject(1, UUID.class),
                    row.getString(2),
                    row.getTimestamp(3).toInstant()),
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
  public void receive(UUID endpointId, String eventType, String signature, byte[] raw) {
    if (signature == null || signature.length() != 71 || !signature.startsWith("sha256="))
      throw unauthorized();
    Link link =
        jdbc
            .query(
                """
select p.id,p.key,g.repository,g.encrypted_secret,p.trashed_at is null and p.archived_at is null
from automation_github g join projects p on p.id=g.project_id where g.endpoint_id=?
""",
                (row, index) ->
                    new Link(
                        row.getLong(1),
                        row.getString(2),
                        row.getString(3),
                        row.getString(4),
                        row.getBoolean(5)),
                endpointId)
            .stream()
            .findFirst()
            .orElseThrow(GithubAutomation::unauthorized);
    if (!signatureMatches(
        encryption.decrypt(link.encryptedSecret(), SECRET.context()), raw, signature))
      throw unauthorized();
    if (!link.active() || !"pull_request".equals(eventType)) return;

    JsonNode message;
    try {
      message = json.readTree(raw);
    } catch (JacksonException error) {
      throw ApiException.badRequest("GitHub webhook body must be JSON.");
    }
    if (message == null || !message.isObject())
      throw ApiException.badRequest("GitHub webhook body must be JSON.");
    if (!"closed".equals(message.path("action").asText())) return;
    var pr = message.path("pull_request");
    if (!pr.path("merged").asBoolean(false)) return;
    if (!link.repository().equalsIgnoreCase(message.path("repository").path("full_name").asText()))
      throw unauthorized();
    long prId = pr.path("id").asLong(0);
    if (prId <= 0) throw ApiException.badRequest("GitHub pull request ID is required.");
    UUID signalId =
        UUID.nameUUIDFromBytes(
            ("github:" + link.projectId() + ":" + prId).getBytes(StandardCharsets.UTF_8));
    String reference = pr.path("title").asText("") + " " + pr.path("head").path("ref").asText("");
    for (int number : taskNumbers(reference, link.projectKey())) {
      jdbc.queryForList(
              "select id from task where project_id=? and number=? and trashed_at is null",
              Long.class,
              link.projectId(),
              number)
          .forEach(
              taskId ->
                  events.publishEvent(
                      new AutomationSignal(
                          signalId,
                          link.projectId(),
                          taskId,
                          AutomationRule.TriggerKind.PULL_REQUEST_MERGED,
                          null,
                          null,
                          null,
                          List.of())));
    }
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

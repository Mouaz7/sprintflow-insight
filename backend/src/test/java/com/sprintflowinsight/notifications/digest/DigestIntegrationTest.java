package com.sprintflowinsight.notifications.digest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sprintflowinsight.auth.*;
import com.sprintflowinsight.notifications.*;
import com.sprintflowinsight.support.ApiIntegrationTest;
import java.time.*;
import java.util.*;
import net.javacrumbs.shedlock.core.*;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestPropertySource(properties = "app.digest.initial-delay-ms=86400000")
class DigestIntegrationTest extends ApiIntegrationTest {
  @Autowired DigestRepository subscriptions;
  @Autowired DigestWorker worker;
  @Autowired DigestScheduler scheduler;
  @Autowired DigestTokens tokens;
  @Autowired NotificationRepository notifications;
  @Autowired UserPreferencesRepository preferences;
  @Autowired LockProvider locks;
  @Autowired javax.sql.DataSource dataSource;
  @MockitoBean DigestMailer mailer;

  @BeforeEach
  void ready() {
    when(mailer.ready()).thenReturn(true);
  }

  private DigestSubscription due(User user) {
    var row = new DigestSubscription(user.getId());
    row.configure(
        DigestFrequency.DAILY,
        "Europe/Stockholm",
        Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS).minusSeconds(3 * 86400));
    return subscriptions.saveAndFlush(row);
  }

  private void notice(User user, Long projectId, String message, Instant at, boolean read) {
    var n = new Notification();
    n.setProjectId(projectId);
    n.setRecipientEmail(user.getEmail());
    n.setMessage(message);
    n.setCreatedAt(at);
    n.setRead(read);
    notifications.saveAndFlush(n);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Owner", "Admin", "Member", "Viewer", "Outsider"})
  void preferencesArePrivateToEveryAuthenticatedAccountRegardlessOfProjectRole(String role)
      throws Exception {
    var owner = user();
    var caller = role.equals("Owner") ? owner : user();
    var project = project(owner, null);
    if (!role.equals("Owner") && !role.equals("Outsider")) member(project, caller, role);
    mvc.perform(get("/api/auth/digest").with(as(caller)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.frequency").value("OFF"))
        .andExpect(jsonPath("$.version").value(0));
    mvc.perform(
            put("/api/auth/digest")
                .with(as(caller))
                .with(csrf())
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"frequency\":\"DAILY\",\"timeZone\":\"Europe/Stockholm\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1));
    var other = user();
    mvc.perform(get("/api/auth/digest").with(as(other)))
        .andExpect(jsonPath("$.frequency").value("OFF"));
  }

  @Test
  void validatesRevisionsZonesAndCsrfWithoutCreatingDefaults() throws Exception {
    var user = user();
    long count = subscriptions.count();
    mvc.perform(get("/api/auth/digest").with(as(user))).andExpect(status().isOk());
    assertThat(subscriptions.count()).isEqualTo(count);
    String body = "{\"frequency\":\"WEEKLY\",\"timeZone\":\"UTC\"}";
    mvc.perform(
            put("/api/auth/digest")
                .with(as(user))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isPreconditionRequired());
    mvc.perform(
            put("/api/auth/digest")
                .with(as(user))
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/auth/digest")
                .with(as(user))
                .with(csrf())
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/auth/digest")
                .with(as(user))
                .with(csrf())
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isPreconditionFailed());
    mvc.perform(
            put("/api/auth/digest")
                .with(as(user))
                .with(csrf())
                .header("If-Match", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("UTC", "Fake/Zone")))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/auth/digest")).andExpect(status().isUnauthorized());
  }

  @Test
  void sendsOnlyUnreadVisibleUpdatesInTheCurrentWindowAndDoesNotSendTwice() {
    var owner = user();
    var recipient = user();
    var visible = project(owner, recipient);
    var outside = project(owner, null);
    Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    var row = due(recipient);
    notice(recipient, visible.getId(), "VISIBLE UPDATE", now.minusSeconds(30), false);
    notice(recipient, visible.getId(), "ALREADY READ", now.minusSeconds(30), true);
    notice(recipient, outside.getId(), "PRIVATE PROJECT", now.minusSeconds(30), false);
    notice(recipient, visible.getId(), "FUTURE", now.plusSeconds(60), false);
    notice(recipient, visible.getId(), "OLD", row.getCheckpointAt().minusSeconds(1), false);
    worker.send(recipient.getId(), now);
    worker.send(recipient.getId(), now);
    var body = ArgumentCaptor.forClass(String.class);
    verify(mailer, times(1)).send(eq(recipient.getEmail()), anyString(), body.capture());
    assertThat(body.getValue())
        .contains("VISIBLE UPDATE", "/email-preferences?token=")
        .doesNotContain("ALREADY READ", "PRIVATE PROJECT", "FUTURE", "OLD");
    var saved = subscriptions.findById(recipient.getId()).orElseThrow();
    assertThat(saved.getLastSentAt()).isEqualTo(now);
    assertThat(saved.getNextDueAt()).isAfter(now);
  }

  @Test
  void unsubscribeNeedsAnAuthenticatedTokenAndOldLinksDoNotDisableAReenabledSubscription()
      throws Exception {
    var user = user();
    var row = due(user);
    String token =
        tokens.create(row, Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    String body = "{\"token\":\"" + token + "\"}";
    mvc.perform(get("/email-preferences?token=" + token)).andExpect(status().isOk());
    assertThat(subscriptions.findById(user.getId()).orElseThrow().getFrequency())
        .isEqualTo(DigestFrequency.DAILY);
    mvc.perform(
            post("/api/digest/unsubscribe")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNoContent());
    mvc.perform(
            post("/api/digest/unsubscribe")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNoContent());
    row = subscriptions.findById(user.getId()).orElseThrow();
    row.configure(
        DigestFrequency.WEEKLY,
        "UTC",
        Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    subscriptions.saveAndFlush(row);
    mvc.perform(
            post("/api/digest/unsubscribe")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/digest/unsubscribe")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"broken\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void skipsWithoutSmtpAndRetriesFailuresWithoutAdvancingTheWindow() {
    var user = user();
    var project = project(user, null);
    var row = due(user);
    Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    notice(user, project.getId(), "UPDATE", now.minusSeconds(1), false);
    when(mailer.ready()).thenReturn(false);
    worker.send(user.getId(), now);
    assertThat(subscriptions.findById(user.getId()).orElseThrow().getNextDueAt())
        .isEqualTo(row.getNextDueAt());
    verify(mailer, never()).send(any(), any(), any());
    when(mailer.ready()).thenReturn(true);
    doThrow(new MailSendException("sensitive-detail-must-not-be-logged"))
        .when(mailer)
        .send(any(), any(), any());
    worker.send(user.getId(), now);
    var saved = subscriptions.findById(user.getId()).orElseThrow();
    assertThat(saved.getCheckpointAt()).isEqualTo(row.getCheckpointAt());
    assertThat(saved.getNextDueAt()).isEqualTo(now.plusSeconds(900));
    assertThat(saved.getLastSentAt()).isNull();
  }

  @Test
  void honorsNotificationOptOutAndEmptyWindowsAndRunsWithShedLock() {
    var user = user();
    var project = project(user, null);
    due(user);
    Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    var pref = new UserPreferences();
    pref.setUserId(user.getId());
    pref.setNotifications(false);
    preferences.saveAndFlush(pref);
    notice(user, project.getId(), "DO NOT SEND", now.minusSeconds(1), false);
    worker.send(user.getId(), now);
    verify(mailer, never()).send(any(), any(), any());
    var another = user();
    due(another);
    worker.send(another.getId(), now);
    verify(mailer, never()).send(any(), any(), any());
    when(mailer.ready()).thenReturn(false);
    scheduler.run();
  }

  @Test
  void databaseLockIsSharedBetweenIndependentProviders() {
    var config =
        new LockConfiguration(
            Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS),
            "digest-test-" + UUID.randomUUID(),
            Duration.ofSeconds(30),
            Duration.ZERO);
    var first = locks.lock(config).orElseThrow();
    var second =
        new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    try {
      assertThat(second.lock(config)).isEmpty();
    } finally {
      first.unlock();
    }
    var acquired = second.lock(config).orElseThrow();
    acquired.unlock();
  }

  @Test
  void aDatabaseRoundedNotificationIsNotLostBetweenDigestWindows() {
    var user = user();
    var project = project(user, null);
    due(user);
    Instant boundary = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    notice(user, project.getId(), "NEXT WINDOW", boundary.plusNanos(700), false);
    worker.send(user.getId(), boundary.plusNanos(600));
    verify(mailer, never()).send(any(), any(), any());
    assertThat(subscriptions.findById(user.getId()).orElseThrow().getCheckpointAt())
        .isEqualTo(boundary);
    new JdbcTemplate(dataSource)
        .update(
            "update digest_subscription set next_due_at=? where user_id=?",
            java.sql.Timestamp.from(boundary),
            user.getId());
    worker.send(user.getId(), boundary.plusNanos(2000));
    verify(mailer, times(1)).send(eq(user.getEmail()), anyString(), contains("NEXT WINDOW"));
  }
}

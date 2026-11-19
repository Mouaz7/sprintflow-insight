package com.sprintflowinsight.notifications.digest;

import com.sprintflowinsight.auth.*;
import com.sprintflowinsight.notifications.NotificationRepository;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DigestWorker {
  private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DigestWorker.class);
  private final DigestRepository subscriptions;
  private final NotificationRepository notifications;
  private final UserRepository users;
  private final UserPreferencesRepository preferences;
  private final DigestMailer mailer;
  private final DigestTokens tokens;
  private final String publicUrl;

  public DigestWorker(
      DigestRepository subscriptions,
      NotificationRepository notifications,
      UserRepository users,
      UserPreferencesRepository preferences,
      DigestMailer mailer,
      DigestTokens tokens,
      @Value("${app.password-reset.base-url:http://localhost:8081}") String publicUrl) {
    this.subscriptions = subscriptions;
    this.notifications = notifications;
    this.users = users;
    this.preferences = preferences;
    this.mailer = mailer;
    this.tokens = tokens;
    this.publicUrl = publicUrl.replaceAll("/+$", "");
  }

  @Transactional
  public void send(Long id, Instant cutoff) {
    if (!mailer.ready()) return;
    cutoff = cutoff.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    var row = subscriptions.lock(id).orElse(null);
    if (row == null
        || row.getFrequency() == DigestFrequency.OFF
        || row.getNextDueAt().isAfter(cutoff)) return;
    var user = users.findById(id).orElse(null);
    if (user == null) return;
    var prefs = preferences.findById(id).orElse(null);
    if (prefs != null && !prefs.isNotifications()) {
      row.completed(cutoff, false);
      return;
    }
    var updates =
        notifications.digestFor(
            id,
            user.getEmail().toLowerCase(Locale.ROOT),
            row.getCheckpointAt(),
            cutoff,
            PageRequest.of(0, 50));
    if (updates.isEmpty()) {
      row.completed(cutoff, false);
      return;
    }
    boolean sv = prefs != null && "SV".equals(prefs.getLanguage());
    String subject = sv ? "Din sammanfattning från SprintFlow" : "Your SprintFlow summary";
    StringBuilder body =
        new StringBuilder(
            sv
                ? "Dina olästa aviseringar (högst 50):\n\n"
                : "Your unread notifications (up to 50):\n\n");
    for (var notification : updates)
      body.append("• ").append(localized(notification.getMessage(), sv)).append('\n');
    body.append("\n")
        .append(publicUrl)
        .append("/overview\n\n")
        .append(sv ? "Avregistrera e-postsammanfattningar: " : "Unsubscribe from email summaries: ")
        .append(publicUrl)
        .append("/email-preferences?token=")
        .append(tokens.create(row, cutoff));
    try {
      mailer.send(user.getEmail(), subject, body.toString());
      row.completed(cutoff, true);
    } catch (MailException failure) {
      row.retryAfter(cutoff);
      LOG.warn("Digest delivery failed for account {}.", id);
    }
  }

  static String localized(String message, boolean sv) {
    if (!sv) return message;
    String[][] prefixes = {
      {"Mentioned you in a comment on “", "Du nämndes i en kommentar på “"},
      {"New comment on “", "Ny kommentar på “"},
      {"You were assigned task “", "Du har tilldelats uppgiften “"}
    };
    for (String[] prefix : prefixes)
      if (message.startsWith(prefix[0])) return prefix[1] + message.substring(prefix[0].length());
    return message;
  }
}

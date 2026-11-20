package com.sprintflowinsight.notifications.digest;

import com.sprintflowinsight.VersionPrecondition;
import com.sprintflowinsight.auth.SessionUser;
import com.sprintflowinsight.auth.UserRepository;
import com.sprintflowinsight.common.error.ApiException;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DigestSettingsService {
  private final DigestRepository repository;
  private final UserRepository users;
  private final SessionUser accounts;
  private final DigestMailer mailer;
  private final DigestTokens tokens;

  public DigestSettingsService(
      DigestRepository repository,
      UserRepository users,
      SessionUser accounts,
      DigestMailer mailer,
      DigestTokens tokens) {
    this.repository = repository;
    this.users = users;
    this.accounts = accounts;
    this.mailer = mailer;
    this.tokens = tokens;
  }

  @Transactional(readOnly = true)
  public DigestResponse read(HttpSession session) {
    return repository
        .findById(accounts.id(session))
        .map(this::response)
        .orElseGet(
            () -> new DigestResponse(DigestFrequency.OFF, "UTC", null, null, 0, mailer.ready()));
  }

  @Transactional
  public DigestResponse save(DigestRequest request, String ifMatch, HttpSession session) {
    Long id = accounts.id(session);
    DigestTiming.zone(request.timeZone());
    // The account lock also serializes first creation and account deletion.
    users.findForProfileUpdate(id).orElseThrow(() -> ApiException.notFound("Account not found."));
    var row = repository.lock(id).orElse(null);
    VersionPrecondition.require(ifMatch, row == null ? 0L : row.getVersion() + 1);
    if (row == null) row = new DigestSubscription(id);
    row.configure(request.frequency(), request.timeZone(), Instant.now());
    return response(repository.saveAndFlush(row));
  }

  @Transactional
  public void unsubscribe(String token) {
    var claim = tokens.verify(token, Instant.now());
    var row =
        repository
            .lock(claim.userId())
            .filter(value -> value.getTokenVersion().equals(claim.version()))
            .orElseThrow(
                () -> ApiException.badRequest("This unsubscribe link is invalid or has expired."));
    row.configure(DigestFrequency.OFF, row.getTimeZone(), Instant.now());
  }

  private DigestResponse response(DigestSubscription row) {
    return new DigestResponse(
        row.getFrequency(),
        row.getTimeZone(),
        row.getNextDueAt(),
        row.getLastSentAt(),
        row.getVersion() + 1,
        mailer.ready());
  }
}

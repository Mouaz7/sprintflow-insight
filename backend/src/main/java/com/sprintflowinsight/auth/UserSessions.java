package com.sprintflowinsight.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * Per-user session control on top of Spring Session. Sessions are indexed by the immutable
 * account id ({@code user:<id>}), never by email, so revocation still works after an email
 * change.
 */
@Service
public class UserSessions {
  private static final Logger SECURITY_LOG = LoggerFactory.getLogger("security.events");

  private final FindByIndexNameSessionRepository<? extends Session> sessions;

  public UserSessions(FindByIndexNameSessionRepository<? extends Session> sessions) {
    this.sessions = sessions;
  }

  static String principal(Long userId) {
    return "user:" + userId;
  }

  /** Associates the current session with the account so it can be found and revoked later. */
  public void bind(HttpSession session, Long userId) {
    session.setAttribute(
        FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal(userId));
  }

  /**
   * Rotates the caller's session id (defeats fixation of a possibly stolen id) and signs the
   * account out everywhere else. Returns the number of other sessions that were revoked.
   */
  public int rotateAndRevokeOthers(HttpServletRequest request, Long userId) {
    // The store still lists this session under its old id until the request commits.
    String previous = request.getSession().getId();
    String current = request.changeSessionId();
    return revoke(userId, java.util.Set.of(previous, current));
  }

  /** Signs the account out of every session, e.g. after a password reset by link or code. */
  public int revokeAll(Long userId) {
    return revoke(userId, java.util.Set.of());
  }

  private int revoke(Long userId, java.util.Set<String> keep) {
    var ids =
        sessions.findByIndexNameAndIndexValue(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal(userId))
            .keySet()
            .stream()
            .filter(id -> !keep.contains(id))
            .toList();
    ids.forEach(sessions::deleteById);
    if (!ids.isEmpty()) SECURITY_LOG.info("Revoked {} session(s) after a credential change.", ids.size());
    return ids.size();
  }
}

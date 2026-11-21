package com.sprintflowinsight.auth;

import com.sprintflowinsight.common.SecureTokens;
import com.sprintflowinsight.common.error.ApiException;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class PasskeyChallenges {
  enum Purpose {
    REGISTER,
    LOGIN
  }

  private final JdbcTemplate jdbc;

  PasskeyChallenges(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  void save(HttpSession session, Purpose purpose, String challenge, Serializable options) {
    jdbc.update(
        """
delete from passkey_challenge where expires_at <= clock_timestamp()
  and ctid in (select ctid from passkey_challenge where expires_at<=clock_timestamp() limit 1000)
""");
    jdbc.update(
        """
        insert into passkey_challenge(session_digest,purpose,challenge_digest,expires_at)
        values(?,?,?,clock_timestamp()+interval '2 minutes')
        on conflict (session_digest,purpose) do update
        set challenge_digest=excluded.challenge_digest,expires_at=excluded.expires_at
        """,
        digest(session.getId()),
        purpose.name(),
        digest(challenge));
    session.setAttribute(key(purpose), options);
  }

  <T> T pending(HttpSession session, Purpose purpose, Class<T> type) {
    Object options = session == null ? null : session.getAttribute(key(purpose));
    if (!type.isInstance(options)) throw expired();
    return type.cast(options);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void claim(HttpSession session, Purpose purpose, String challenge) {
    if (jdbc.update(
            """
            delete from passkey_challenge where session_digest=? and purpose=?
              and challenge_digest=? and expires_at>clock_timestamp()
            """,
            digest(session.getId()),
            purpose.name(),
            digest(challenge))
        != 1) throw expired();
    session.removeAttribute(key(purpose));
  }

  private static String key(Purpose purpose) {
    return PasskeyChallenges.class.getName() + "." + purpose.name();
  }

  private static String digest(String value) {
    return SecureTokens.sha256(value);
  }

  private static ApiException expired() {
    return ApiException.badRequest("Start a new passkey request.");
  }
}

package com.sprintflowinsight.auth;

import com.sprintflowinsight.common.SecureTokens;
import com.sprintflowinsight.common.crypto.EncryptedColumn;
import com.sprintflowinsight.common.crypto.EncryptionService;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.common.error.ErrorCode;
import jakarta.servlet.http.HttpSession;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MfaService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String ISSUER = "SprintFlow";
  private static final int MAX_ATTEMPTS = 5;
  private static final int MAX_ACCOUNT_FAILURES = 10;
  private static final int RECOVERY_CODES = 10;
  private static final EncryptedColumn SECRET =
      EncryptedColumn.of("account_mfa", "user_id", "secret_ciphertext");

  record Settings(boolean enabled, int recoveryCodesRemaining, boolean requiredByOrganization) {}

  record Setup(String secret, String uri) {
    @Override
    public String toString() {
      return "MfaSetup[secret=redacted]";
    }
  }

  record Enabled(List<String> recoveryCodes) {
    @Override
    public String toString() {
      return "MfaEnabled[recoveryCodes=redacted]";
    }
  }

  record Account(
      String ciphertext,
      OffsetDateTime created,
      boolean enabled,
      Long lastStep,
      int failures,
      OffsetDateTime lastFailure) {}

  record Challenge(int attempts, String credentialDigest) {}

  enum Outcome {
    ACCEPTED,
    INVALID,
    EXHAUSTED
  }

  record Attempt(Outcome outcome, User user) {}

  private final JdbcTemplate jdbc;
  private final SessionUser sessionUser;
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final EncryptionService encryption;

  MfaService(
      JdbcTemplate jdbc,
      SessionUser sessionUser,
      UserRepository users,
      PasswordEncoder passwords,
      EncryptionService encryption) {
    this.jdbc = jdbc;
    this.sessionUser = sessionUser;
    this.users = users;
    this.passwords = passwords;
    this.encryption = encryption;
  }

  @Bean
  EncryptedColumn encryptedMfaSecret() {
    return SECRET;
  }

  public boolean organizationRequires(long userId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
select exists(
  select 1 from organization o where o.require_mfa and (
    exists (select 1 from organization_member m where m.organization_id=o.id and m.user_id=?)
    or exists (select 1 from projects p join project_member m on m.project_id=p.id
               where p.organization_id=o.id and p.trashed_at is null and m.user_id=?)
  )
)
""",
            Boolean.class,
            userId,
            userId));
  }

  private boolean isEnabled(long userId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            select exists(select 1 from account_mfa where user_id=? and enabled_at is not null)
            """,
            Boolean.class,
            userId));
  }

  public boolean workspaceAllowed(UserIdentity user) {
    return user.mfaVerified()
        || (!isEnabled(user.userId()) && !organizationRequires(user.userId()));
  }

  public boolean needsReauthentication(UserIdentity user) {
    return !user.mfaVerified() && isEnabled(user.userId());
  }

  @Transactional(readOnly = true)
  public Settings settings(HttpSession session) {
    long userId = sessionUser.id(session);
    return settings(userId, organizationRequires(userId));
  }

  private Settings settings(long userId, boolean required) {
    return jdbc
        .query(
            """
select m.enabled_at is not null, (select count(*) from account_mfa_recovery r where r.user_id=m.user_id)
from account_mfa m where m.user_id=?
""",
            (row, index) -> new Settings(row.getBoolean(1), row.getInt(2), required),
            userId)
        .stream()
        .findFirst()
        .orElse(new Settings(false, 0, required));
  }

  @Transactional
  public Setup setup(String password, HttpSession session) {
    User user = password(password, session);
    if (account(user.getId()).filter(Account::enabled).isPresent())
      throw ApiException.conflict("Two-factor authentication is already enabled.");
    byte[] raw = new byte[TotpCodes.SECRET_BYTES];
    RANDOM.nextBytes(raw);
    String ciphertext =
        encryption.encrypt(Base64.getEncoder().encodeToString(raw), SECRET.context());
    jdbc.update(
        """
        insert into account_mfa(user_id,secret_ciphertext) values(?,?)
        on conflict (user_id) do update set secret_ciphertext=excluded.secret_ciphertext,
        created_at=clock_timestamp()
        """,
        user.getId(),
        ciphertext);
    String secret = TotpCodes.base32(raw);
    String label = URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8).replace("+", "%20");
    return new Setup(
        secret,
        "otpauth://totp/"
            + ISSUER
            + ":"
            + label
            + "?secret="
            + secret
            + "&issuer="
            + ISSUER
            + "&algorithm=SHA1&digits=6&period=30");
  }

  @Transactional
  public Enabled enable(String password, String code, HttpSession session) {
    User user = password(password, session);
    Account mfa =
        account(user.getId())
            .filter(
                item ->
                    !item.enabled()
                        && item.created().toInstant().isAfter(Instant.now().minusSeconds(600)))
            .orElseThrow(() -> ApiException.conflict("Start a new two-factor setup first."));
    long step =
        TotpCodes.match(secret(mfa), code, Instant.now(), -1).orElseThrow(MfaService::invalidCode);
    jdbc.update(
        "update account_mfa set enabled_at=clock_timestamp(), last_step=? where user_id=?",
        step,
        user.getId());
    List<String> recovery = new ArrayList<>(RECOVERY_CODES);
    for (int i = 0; i < RECOVERY_CODES; i++) {
      byte[] raw = new byte[10];
      RANDOM.nextBytes(raw);
      String plain = HexFormat.of().withUpperCase().formatHex(raw);
      recovery.add(
          String.join(
              "-",
              plain.substring(0, 5),
              plain.substring(5, 10),
              plain.substring(10, 15),
              plain.substring(15, 20)));
      jdbc.update(
          "insert into account_mfa_recovery(user_id,digest) values(?,?)",
          user.getId(),
          SecureTokens.sha256(plain));
    }
    return new Enabled(List.copyOf(recovery));
  }

  @Transactional(noRollbackFor = ApiException.class)
  public void disable(String password, String code, HttpSession session) {
    User user = password(password, session);
    if (organizationRequires(user.getId()))
      throw ApiException.conflict(
          "An organization requires two-factor authentication for this account.");
    Account mfa =
        account(user.getId())
            .filter(Account::enabled)
            .orElseThrow(
                () -> ApiException.conflict("Two-factor authentication is already disabled."));
    if (limited(mfa)) throw rateLimited();
    if (!consume(user.getId(), mfa, code)) {
      recordFailure(user.getId());
      throw invalidCode();
    }
    jdbc.update("delete from account_mfa where user_id=?", user.getId());
  }

  @Transactional
  public Optional<String> begin(User user) {
    if (account(user.getId()).filter(Account::enabled).isEmpty()) return Optional.empty();
    jdbc.update("delete from account_mfa_challenge where user_id=?", user.getId());
    String challenge = SecureTokens.token();
    jdbc.update(
        """
        insert into account_mfa_challenge(digest,user_id,credential_digest,expires_at)
        values(?,?,?,clock_timestamp()+interval '5 minutes')
        """,
        SecureTokens.sha256(challenge),
        user.getId(),
        SecureTokens.sha256(user.getPassword()));
    return Optional.of(challenge);
  }

  @Transactional
  public Attempt verify(String token, String code) {
    if (token == null) return new Attempt(Outcome.INVALID, null);
    String digest = SecureTokens.sha256(token);
    var id =
        jdbc
            .queryForList(
                "select user_id from account_mfa_challenge where digest=?", Long.class, digest)
            .stream()
            .findFirst();
    if (id.isEmpty()) return new Attempt(Outcome.INVALID, null);
    long userId = id.get();
    var mfa = account(userId).filter(Account::enabled);
    if (mfa.isEmpty()) return new Attempt(Outcome.INVALID, null);
    var challenge =
        jdbc
            .query(
                """
                select attempts,credential_digest from account_mfa_challenge
                where digest=? and expires_at>clock_timestamp()
                """,
                (row, index) -> new Challenge(row.getInt(1), row.getString(2)),
                digest)
            .stream()
            .findFirst();
    if (challenge.isEmpty()) return new Attempt(Outcome.INVALID, null);
    if (challenge.get().attempts() >= MAX_ATTEMPTS || limited(mfa.get()))
      return new Attempt(Outcome.EXHAUSTED, null);
    var user =
        users
            .findById(userId)
            .filter(
                item ->
                    SecureTokens.sha256(item.getPassword())
                        .equals(challenge.get().credentialDigest()));
    if (user.isEmpty()) {
      jdbc.update("delete from account_mfa_challenge where digest=?", digest);
      return new Attempt(Outcome.INVALID, null);
    }
    if (!consume(userId, mfa.get(), code)) {
      jdbc.update("update account_mfa_challenge set attempts=attempts+1 where digest=?", digest);
      recordFailure(userId);
      return new Attempt(Outcome.INVALID, null);
    }
    jdbc.update("delete from account_mfa_challenge where digest=?", digest);
    jdbc.update("update account_mfa set failures=0,last_failure=null where user_id=?", userId);
    return new Attempt(Outcome.ACCEPTED, user.get());
  }

  private User password(String supplied, HttpSession session) {
    return users
        .findForProfileUpdate(sessionUser.id(session))
        .filter(user -> passwords.matches(supplied, user.getPassword()))
        .orElseThrow(
            () -> new ApiException(ErrorCode.UNAUTHENTICATED, "Current password is incorrect."));
  }

  /** Reads are explicitly locked while a setup, code or challenges can be changed. */
  private Optional<Account> account(long userId) {
    return jdbc
        .query(
            """
select secret_ciphertext,created_at,enabled_at is not null,last_step,failures,last_failure
from account_mfa where user_id=? for update
""",
            MfaService::accountRow,
            userId)
        .stream()
        .findFirst();
  }

  private static boolean limited(Account account) {
    return account.failures() >= MAX_ACCOUNT_FAILURES
        && account.lastFailure() != null
        && account.lastFailure().toInstant().isAfter(Instant.now().minusSeconds(600));
  }

  private void recordFailure(long userId) {
    jdbc.update(
        """
        update account_mfa
        set failures=case when last_failure > clock_timestamp()-interval '10 minutes'
                          then least(failures+1,10) else 1 end,
            last_failure=clock_timestamp()
        where user_id=?
        """,
        userId);
  }

  static ApiException rateLimited() {
    return new ApiException(
        ErrorCode.RATE_LIMITED, "Too many two-factor attempts. Wait before trying again.");
  }

  private boolean consume(long userId, Account account, String text) {
    String code = text.strip();
    if (code.matches("[0-9]{6}")) {
      var step =
          TotpCodes.match(
              secret(account),
              code,
              Instant.now(),
              account.lastStep() == null ? -1 : account.lastStep());
      if (step.isEmpty()) return false;
      jdbc.update("update account_mfa set last_step=? where user_id=?", step.getAsLong(), userId);
      return true;
    }
    String recovery = code.replace("-", "").toUpperCase(Locale.ROOT);
    return recovery.matches("[A-F0-9]{20}")
        && jdbc.update(
                "delete from account_mfa_recovery where user_id=? and digest=?",
                userId,
                SecureTokens.sha256(recovery))
            == 1;
  }

  private byte[] secret(Account account) {
    return Base64.getDecoder().decode(encryption.decrypt(account.ciphertext(), SECRET.context()));
  }

  private static Account accountRow(ResultSet row, int index) throws SQLException {
    return new Account(
        row.getString(1),
        row.getObject(2, OffsetDateTime.class),
        row.getBoolean(3),
        row.getObject(4, Long.class),
        row.getInt(5),
        row.getObject(6, OffsetDateTime.class));
  }

  private static ApiException invalidCode() {
    return new ApiException(
        ErrorCode.UNAUTHENTICATED, "The two-factor code is invalid or has already been used.");
  }
}

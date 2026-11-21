package com.sprintflowinsight.auth;

import com.sprintflowinsight.common.SecureTokens;
import com.sprintflowinsight.common.error.ApiException;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PersonalAccessTokens {
  private static final String PREFIX = "sf_pat_";
  private static final Pattern FORMAT = Pattern.compile(PREFIX + "[A-Za-z0-9_-]{43}");

  enum Scope {
    PROJECT_READ,
    TASK_READ,
    TASK_WRITE
  }

  record Draft(
      @NotBlank @Size(max = 80) String label,
      @NotEmpty @Size(max = 3) Set<@NotNull Scope> scopes,
      @NotNull @Min(1) @Max(90) Integer expiresInDays) {}

  record Token(
      long id,
      String label,
      List<Scope> scopes,
      Instant issuedAt,
      Instant expiresAt,
      Instant lastUsedAt) {}

  record Issued(Token metadata, String token) {
    @Override
    public String toString() {
      return "IssuedPersonalAccessToken[token=redacted]";
    }
  }

  private final JdbcTemplate jdbc;
  private final SessionUser account;
  private final UserRepository users;
  private final MfaService mfa;

  PersonalAccessTokens(
      JdbcTemplate jdbc, SessionUser account, UserRepository users, MfaService mfa) {
    this.jdbc = jdbc;
    this.account = account;
    this.users = users;
    this.mfa = mfa;
  }

  @Transactional(readOnly = true)
  public List<Token> list(HttpSession session) {
    return jdbc.query(
        """
        select id,label,array_to_string(scopes,','),issued_at,expires_at,last_used_at
        from personal_access_token where user_id=? order by id desc limit 100
        """,
        PersonalAccessTokens::metadata,
        account.id(session));
  }

  @Transactional
  public Issued issue(Draft draft, HttpSession session) {
    long owner = account.id(session);
    boolean verified = account.mfaVerified();
    if (mfa.organizationRequires(owner) && !verified) {
      throw ApiException.forbidden(
          "Verify two-factor authentication before creating an access token.");
    }
    users
        .findForProfileUpdate(owner)
        .orElseThrow(() -> ApiException.notFound("Account not found."));
    jdbc.update(
        "delete from personal_access_token where user_id=? and expires_at<=clock_timestamp()",
        owner);
    Integer total =
        jdbc.queryForObject(
            "select count(*) from personal_access_token where user_id=?", Integer.class, owner);
    if (total != null && total >= 25)
      throw ApiException.conflict("Revoke a token before creating another.");
    String token = PREFIX + SecureTokens.token();
    String permissions =
        draft.scopes().stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    Token metadata =
        jdbc.queryForObject(
            """
insert into personal_access_token(user_id,label,token_digest,scopes,mfa_verified,expires_at)
values(?,?,?,string_to_array(?,','),?,clock_timestamp() + make_interval(days => ?))
returning id,label,array_to_string(scopes,','),issued_at,expires_at,last_used_at
""",
            PersonalAccessTokens::metadata,
            owner,
            draft.label().strip(),
            SecureTokens.sha256(token),
            permissions,
            verified,
            draft.expiresInDays());
    return new Issued(metadata, token);
  }

  @Transactional
  public void revoke(long id, HttpSession session) {
    if (jdbc.update(
            "delete from personal_access_token where id=? and user_id=?", id, account.id(session))
        == 0) throw ApiException.notFound("Access token not found.");
  }

  @Transactional
  public Optional<Authentication> authenticate(String raw) {
    if (raw == null || raw.length() != PREFIX.length() + 43 || !FORMAT.matcher(raw).matches())
      return Optional.empty();
    String digest = SecureTokens.sha256(raw);
    var candidates =
        jdbc.query(
            """
            select t.user_id,u.email,t.mfa_verified,array_to_string(t.scopes,',')
            from personal_access_token t join users u on u.id=t.user_id
            where t.token_digest=? and t.expires_at>clock_timestamp()
            """,
            (row, index) ->
                new Candidate(
                    new UserIdentity(row.getLong(1), row.getString(2), row.getBoolean(3), 0),
                    scopes(row.getString(4))),
            digest);
    if (candidates.isEmpty()) return Optional.empty();
    Candidate candidate = candidates.getFirst();
    if (!mfa.workspaceAllowed(candidate.user())) return Optional.empty();
    if (jdbc.update(
            """
            update personal_access_token set last_used_at=clock_timestamp()
            where token_digest=? and expires_at>clock_timestamp()
            """,
            digest)
        != 1) return Optional.empty();
    return Optional.of(
        UsernamePasswordAuthenticationToken.authenticated(
            candidate.user(),
            null,
            candidate.scopes().stream()
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope.name()))
                .toList()));
  }

  private record Candidate(UserIdentity user, List<Scope> scopes) {}

  private static Token metadata(ResultSet row, int index) throws SQLException {
    var lastUsed = row.getTimestamp(6);
    return new Token(
        row.getLong(1),
        row.getString(2),
        scopes(row.getString(3)),
        row.getTimestamp(4).toInstant(),
        row.getTimestamp(5).toInstant(),
        lastUsed == null ? null : lastUsed.toInstant());
  }

  private static List<Scope> scopes(String value) {
    return Arrays.stream(value.split(",")).map(Scope::valueOf).toList();
  }
}

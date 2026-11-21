package com.sprintflowinsight.auth;

import com.sprintflowinsight.auth.PasskeyChallenges.Purpose;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.common.error.ErrorCode;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.webauthn.api.*;
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule;
import org.springframework.security.web.webauthn.management.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
class PasskeyService {
  private static final Duration WINDOW = Duration.ofMinutes(2);
  private static final Duration RECENT_SIGN_IN = Duration.ofMinutes(10);

  record Configuration(boolean available, String origin) {}

  record Credential(String id, String label, Instant created, Instant lastUsed) {}

  private final UserRepository users;
  private final SessionUser account;
  private final PasskeyChallenges challenges;
  private final PublicKeyCredentialUserEntityRepository identities;
  private final UserCredentialRepository credentials;
  private final Webauthn4JRelyingPartyOperations operations;
  private final Configuration configuration;
  private final JsonMapper json =
      JsonMapper.builder().addModule(new WebauthnJacksonModule()).build();

  PasskeyService(
      UserRepository users,
      SessionUser account,
      PasskeyChallenges challenges,
      JdbcTemplate jdbc,
      @Value("${app.password-reset.base-url:http://localhost:8081}") String publicUrl) {
    this.users = users;
    this.account = account;
    this.challenges = challenges;
    this.identities = new JdbcPublicKeyCredentialUserEntityRepository(jdbc);
    this.credentials = new JdbcUserCredentialRepository(jdbc);
    URI url = URI.create(publicUrl);
    String host = url.getHost();
    boolean available =
        host != null
            && url.getUserInfo() == null
            && ("https".equalsIgnoreCase(url.getScheme())
                || ("http".equalsIgnoreCase(url.getScheme())
                    && "localhost".equalsIgnoreCase(host)));
    if (available) {
      String scheme = url.getScheme().toLowerCase(Locale.ROOT);
      int port = url.getPort();
      boolean explicitPort = port > 0 && port != ("https".equals(scheme) ? 443 : 80);
      String origin =
          scheme + "://" + host.toLowerCase(Locale.ROOT) + (explicitPort ? ":" + port : "");
      var rp =
          PublicKeyCredentialRpEntity.builder()
              .id(host.toLowerCase(Locale.ROOT))
              .name("SprintFlow")
              .build();
      operations =
          new Webauthn4JRelyingPartyOperations(identities, credentials, rp, Set.of(origin));
      operations.setCustomizeCreationOptions(
          options ->
              options
                  .timeout(WINDOW)
                  .authenticatorSelection(
                      AuthenticatorSelectionCriteria.builder()
                          .residentKey(ResidentKeyRequirement.REQUIRED)
                          .userVerification(UserVerificationRequirement.REQUIRED)
                          .build()));
      operations.setCustomizeRequestOptions(
          options ->
              options.timeout(WINDOW).userVerification(UserVerificationRequirement.REQUIRED));
      configuration = new Configuration(true, origin);
    } else {
      configuration = new Configuration(false, null);
      operations = null;
    }
  }

  Configuration configuration() {
    return configuration;
  }

  @Transactional(readOnly = true)
  public List<Credential> list(HttpSession session) {
    return credentials(account.id(session)).stream()
        .sorted(Comparator.comparing(CredentialRecord::getCreated).reversed())
        .map(PasskeyService::summary)
        .toList();
  }

  @Transactional
  public void delete(String slug, HttpSession session) {
    long user = recent(session);
    var selected =
        credentials(user).stream()
            .filter(item -> item.getCredentialId().toBase64UrlString().equals(slug))
            .findFirst()
            .orElseThrow(() -> ApiException.notFound("Passkey not found."));
    credentials.delete(selected.getCredentialId());
  }

  @Transactional
  public JsonNode registrationOptions(HttpSession session) {
    long user = recent(session);
    var current =
        users
            .findForProfileUpdate(user)
            .orElseThrow(() -> ApiException.notFound("Account not found."));
    if (credentials(user).size() >= 20)
      throw ApiException.conflict("Remove a passkey before adding another.");
    var entity = identities.findByUsername(Long.toString(user));
    if (entity == null) {
      identities.save(
          ImmutablePublicKeyCredentialUserEntity.builder()
              .name(Long.toString(user))
              .id(Bytes.random())
              .displayName(current.getEmail())
              .build());
    }
    var principal =
        UsernamePasswordAuthenticationToken.authenticated(Long.toString(user), null, List.of());
    var options =
        relyingParty()
            .createPublicKeyCredentialCreationOptions(
                new ImmutablePublicKeyCredentialCreationOptionsRequest(principal));
    challenges.save(session, Purpose.REGISTER, options.getChallenge().toBase64UrlString(), options);
    return json.valueToTree(options);
  }

  @Transactional
  public Credential register(String label, JsonNode data, HttpSession session) {
    long user = recent(session);
    var options =
        challenges.pending(session, Purpose.REGISTER, PublicKeyCredentialCreationOptions.class);
    if (!Long.toString(user).equals(options.getUser().getName())) throw denied();
    challenges.claim(session, Purpose.REGISTER, options.getChallenge().toBase64UrlString());
    users.findForProfileUpdate(user).orElseThrow(PasskeyService::denied);
    if (credentials(user).size() >= 20)
      throw ApiException.conflict("Remove a passkey before adding another.");
    try {
      var credential =
          decode(
              data, new TypeReference<PublicKeyCredential<AuthenticatorAttestationResponse>>() {});
      var result =
          relyingParty()
              .registerCredential(
                  new ImmutableRelyingPartyRegistrationRequest(
                      options, new RelyingPartyPublicKey(credential, label.strip())));
      return summary(result);
    } catch (tools.jackson.core.JacksonException
        | IllegalArgumentException
        | AuthenticationException
        | com.webauthn4j.verifier.exception.VerificationException error) {
      throw ApiException.badRequest("The passkey could not be registered.");
    }
  }

  public JsonNode loginOptions(HttpSession session) {
    var options =
        relyingParty()
            .createCredentialRequestOptions(
                new ImmutablePublicKeyCredentialRequestOptionsRequest(null));
    challenges.save(session, Purpose.LOGIN, options.getChallenge().toBase64UrlString(), options);
    return json.valueToTree(options);
  }

  @Transactional
  public User authenticate(JsonNode data, HttpSession session) {
    var options =
        challenges.pending(session, Purpose.LOGIN, PublicKeyCredentialRequestOptions.class);
    challenges.claim(session, Purpose.LOGIN, options.getChallenge().toBase64UrlString());
    try {
      var credential =
          decode(data, new TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse>>() {});
      var identity =
          relyingParty().authenticate(new RelyingPartyAuthenticationRequest(options, credential));
      return users.findById(Long.parseLong(identity.getName())).orElseThrow(PasskeyService::denied);
    } catch (tools.jackson.core.JacksonException
        | IllegalArgumentException
        | AuthenticationException
        | com.webauthn4j.verifier.exception.VerificationException error) {
      throw denied();
    }
  }

  private <T> T decode(JsonNode data, TypeReference<T> type) {
    String payload = data.toString();
    if (payload.length() > 64 * 1024)
      throw ApiException.badRequest("Passkey response is too large.");
    return json.readValue(payload, type);
  }

  private Webauthn4JRelyingPartyOperations relyingParty() {
    if (operations == null)
      throw ApiException.badRequest("Passkeys require the configured secure site address.");
    return operations;
  }

  private List<CredentialRecord> credentials(long user) {
    var identity = identities.findByUsername(Long.toString(user));
    return identity == null ? List.of() : credentials.findByUserId(identity.getId());
  }

  private long recent(HttpSession session) {
    long user = account.id(session);
    if (account.authenticatedAt().isBefore(Instant.now().minus(RECENT_SIGN_IN)))
      throw ApiException.forbidden("Sign in again before changing your passkeys.");
    return user;
  }

  private static Credential summary(CredentialRecord record) {
    return new Credential(
        record.getCredentialId().toBase64UrlString(),
        record.getLabel(),
        record.getCreated(),
        record.getLastUsed());
  }

  private static ApiException denied() {
    return new ApiException(ErrorCode.UNAUTHENTICATED, "The passkey could not be verified.");
  }
}

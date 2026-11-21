package com.sprintflowinsight.auth;

import com.sprintflowinsight.ApiExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

@org.springframework.stereotype.Service
public class GoogleLoginService {
  private static final org.slf4j.Logger SECURITY_LOG =
      org.slf4j.LoggerFactory.getLogger("security.events");
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final GoogleTokenVerifier googleTokens;
  private final MfaSignIn signIn;
  private final String googleClientId;

  public GoogleLoginService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      GoogleTokenVerifier googleTokens,
      MfaSignIn signIn,
      @Value("${app.google.client-id:}") String googleClientId) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.googleTokens = googleTokens;
    this.signIn = signIn;
    this.googleClientId = googleClientId;
  }

  public ResponseEntity<?> googleLoginConfiguration() {
    String clientId = activeGoogleClientId();
    return ResponseEntity.ok(Map.of("clientId", clientId));
  }

  public ResponseEntity<?> loginWithGoogle(
      GoogleLoginRequest request,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    String clientId = activeGoogleClientId();
    if (clientId.isBlank()) {
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
          "Google login is not configured yet.");
    }
    var verified = googleTokens.verify(request.credential(), clientId);
    if (verified.isEmpty())
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.UNAUTHORIZED,
          "Google could not verify this account.");
    var payload = verified.get();
    {
      String email = payload.email().trim().toLowerCase(java.util.Locale.ROOT);
      User user =
          userRepository
              .findByEmail(email)
              .orElseGet(
                  () -> {
                    User created = new User();
                    created.setEmail(email);
                    created.setPassword(passwordEncoder.encode(createToken()));
                    created.setDisplayName(payload.name());
                    created.setAvatarImage(payload.picture());
                    return userRepository.save(created);
                  });
      return signIn.afterPrimary(user, servletRequest, servletResponse);
    }
  }

  public String activeGoogleClientId() {
    return googleClientId;
  }

  private String createToken() {
    return com.sprintflowinsight.common.SecureTokens.token();
  }
}

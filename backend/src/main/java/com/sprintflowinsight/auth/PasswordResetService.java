package com.sprintflowinsight.auth;

import com.sprintflowinsight.ApiExceptionHandler;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class PasswordResetService {
  private static final org.slf4j.Logger SECURITY_LOG =
      org.slf4j.LoggerFactory.getLogger("security.events");
  private volatile String unusedRecoveryHash;
  private final UserRepository userRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserSessions userSessions;
  private final org.springframework.beans.factory.ObjectProvider<JavaMailSender> mailSenders;
  private final String resetBaseUrl;
  private final String resetFrom;

  public PasswordResetService(
      UserRepository userRepository,
      PasswordResetTokenRepository passwordResetTokenRepository,
      PasswordEncoder passwordEncoder,
      UserSessions userSessions,
      org.springframework.beans.factory.ObjectProvider<JavaMailSender> mailSenders,
      @Value("${app.password-reset.base-url:http://localhost:8081}") String resetBaseUrl,
      @Value("${app.password-reset.from:}") String resetFrom) {
    this.userRepository = userRepository;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.userSessions = userSessions;
    this.mailSenders = mailSenders;
    this.resetBaseUrl = resetBaseUrl;
    this.resetFrom = resetFrom;
  }

  @Transactional
  public ResponseEntity<?> requestPasswordReset(PasswordResetRequest request) {
    JavaMailSender activeMailSender = activeMailSender();
    String activeResetFrom = activeResetFrom();
    if (!emailReady()) {
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
          "Password-reset emails are temporarily unavailable. The app administrator must configure"
              + " the email service. Please try again later.");
    }

    userRepository
        .findByEmail(request.email().trim().toLowerCase())
        .ifPresent(
            user -> {
              passwordResetTokenRepository.deleteByUser(user);
              String token = createToken();
              PasswordResetToken resetToken = new PasswordResetToken();
              resetToken.setUser(user);
              resetToken.setTokenHash(hash(token));
              resetToken.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
              passwordResetTokenRepository.save(resetToken);

              SimpleMailMessage message = new SimpleMailMessage();
              message.setFrom(activeResetFrom);
              message.setTo(user.getEmail());
              message.setSubject("Reset your SprintFlow password");
              message.setText(
                  "Use this link within 30 minutes to choose a new password:\n\n"
                      + resetBaseUrl
                      + "/reset-password?token="
                      + token);
              activeMailSender.send(message);
            });
    return ResponseEntity.ok(
        Map.of("message", "If this email is registered, a reset link has been sent."));
  }

  public ResponseEntity<?> passwordRecoveryAvailable() {
    return ResponseEntity.ok(Map.of("available", emailReady()));
  }

  /**
   * Unknown account, account without a recovery code and wrong code all produce the same 401 and
   * cost one BCrypt comparison, so the endpoint does not reveal which accounts exist or have a
   * code.
   */
  @Transactional
  public ResponseEntity<?> resetPasswordWithRecoveryCode(RecoveryPasswordResetRequest request) {
    Optional<User> existingUser =
        userRepository.findByEmail(request.email().trim().toLowerCase(java.util.Locale.ROOT));
    String storedHash = existingUser.map(User::getRecoveryCodeHash).orElse(null);
    boolean matches =
        passwordEncoder.matches(
            request.recoveryCode(), storedHash == null ? unusedRecoveryHash() : storedHash);
    if (storedHash == null || !matches) {
      SECURITY_LOG.warn("Rejected a recovery-code password reset.");
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.UNAUTHORIZED, "Email or recovery code is incorrect.");
    }
    User user = existingUser.get();
    user.setPassword(passwordEncoder.encode(request.newPassword()));
    userRepository.save(user);
    userSessions.revokeAll(user.getId());
    return ResponseEntity.ok(Map.of("message", "Password updated. You can now log in."));
  }

  @Transactional
  public ResponseEntity<?> confirmPasswordReset(PasswordResetConfirmRequest request) {
    Optional<PasswordResetToken> resetToken =
        passwordResetTokenRepository.findByTokenHashAndExpiresAtAfter(
            hash(request.token()), Instant.now());
    if (resetToken.isEmpty()) {
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "This reset link is invalid or has expired.");
    }
    User user = resetToken.get().getUser();
    user.setPassword(passwordEncoder.encode(request.newPassword()));
    userRepository.save(user);
    passwordResetTokenRepository.delete(resetToken.get());
    userSessions.revokeAll(user.getId());
    return ResponseEntity.ok(Map.of("message", "Password updated. You can now log in."));
  }

  private String unusedRecoveryHash() {
    if (unusedRecoveryHash == null) unusedRecoveryHash = passwordEncoder.encode(createToken());
    return unusedRecoveryHash;
  }

  private JavaMailSender activeMailSender() {
    return mailSenders.getIfAvailable();
  }

  public boolean emailReady() {
    JavaMailSender sender = activeMailSender();
    if (sender == null || activeResetFrom().isBlank()) return false;
    if (sender instanceof JavaMailSenderImpl configured)
      return configured.getUsername() != null
          && !configured.getUsername().isBlank()
          && configured.getPassword() != null
          && !configured.getPassword().isBlank();
    return true;
  }

  private String activeResetFrom() {
    return resetFrom;
  }

  private String hash(String value) {
    return com.sprintflowinsight.common.SecureTokens.sha256(value);
  }

  private String createToken() {
    return com.sprintflowinsight.common.SecureTokens.token();
  }
}

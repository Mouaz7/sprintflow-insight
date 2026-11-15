package com.sprintflowinsight.auth;

import com.sprintflowinsight.ApiExceptionHandler;
import com.sprintflowinsight.notifications.NotificationService;
import com.sprintflowinsight.workspace.activity.ActivityService;
import com.sprintflowinsight.workspace.collaboration.TaskCommentRepository;
import com.sprintflowinsight.workspace.project.ProjectMemberRepository;
import com.sprintflowinsight.workspace.project.ProjectRepository;
import com.sprintflowinsight.workspace.task.TaskRepository;
import com.sprintflowinsight.workspace.task.TaskStatusHistoryRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private static final org.slf4j.Logger SECURITY_LOG =
      org.slf4j.LoggerFactory.getLogger("security.events");

  @Autowired private UserRepository userRepository;

  @Autowired private PasswordResetTokenRepository passwordResetTokenRepository;

  @Autowired private ProjectRepository projectRepository;

  @Autowired private ProjectMemberRepository projectMemberRepository;

  @Autowired private TaskRepository taskRepository;

  @Autowired private TaskCommentRepository taskCommentRepository;

  @Autowired private TaskStatusHistoryRepository taskStatusHistoryRepository;

  @Autowired private NotificationService notificationService;

  @Autowired private ActivityService activityService;

  @Autowired private SessionUser sessionUser;

  @Autowired private UserSessions userSessions;

  @Autowired private GoogleTokenVerifier googleTokens;

  /** Compared against when an account has no recovery code, so timing does not reveal that. */
  private volatile String unusedRecoveryHash;

  private String unusedRecoveryHash() {
    if (unusedRecoveryHash == null) unusedRecoveryHash = passwordEncoder.encode(createToken());
    return unusedRecoveryHash;
  }

  @Autowired(required = false)
  private JavaMailSender mailSender;

  @Value("${app.password-reset.base-url:http://localhost:8081}")
  private String resetBaseUrl;

  @Value("${app.password-reset.from:}")
  private String resetFrom;

  @Value("${app.google.client-id:}")
  private String googleClientId;

  @Autowired
  private com.sprintflowinsight.workspace.collaboration.RetrospectiveRepository retrospectives;

  @Autowired private com.sprintflowinsight.reporting.SharedReportRepository sharedReports;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private AuthenticationManager authenticationManager;
  @Autowired private SessionAuthenticationService authenticationSessions;
  @Autowired private UserEmailService userEmails;

  @GetMapping("/integrations")
  public Map<String, Boolean> integrations(HttpSession session) {
    sessionUser.email(session);
    return Map.of("gmailReady", emailReady(), "googleReady", !activeGoogleClientId().isBlank());
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @GetMapping("/me")
  public User me(HttpSession session) {
    return userRepository
        .findByEmail(sessionUser.email(session))
        .orElseThrow(
            () ->
                new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED));
  }

  public record Credentials(
      @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String email,
      @jakarta.validation.constraints.NotBlank
          @jakarta.validation.constraints.Size(min = 6, max = 72)
          String password,
      @jakarta.validation.constraints.Size(max = 80) String displayName) {}

  @PostMapping("/register")
  public ResponseEntity<?> register(
      @Valid @RequestBody Credentials credentials,
      HttpServletRequest request,
      HttpServletResponse response) {
    String email = credentials.email().trim().toLowerCase(java.util.Locale.ROOT);
    if (userRepository.findByEmail(email).isPresent())
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.CONFLICT, "Email is already registered.");
    if (credentials.displayName() == null || credentials.displayName().isBlank())
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.BAD_REQUEST, "Name is required.");
    User user = new User();
    user.setEmail(email);
    user.setPassword(passwordEncoder.encode(credentials.password()));
    user.setDisplayName(credentials.displayName().trim());
    User saved = userRepository.save(user);
    authenticationSessions.signIn(saved.getEmail(), request, response);
    return ResponseEntity.ok(saved);
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(
      @Valid @RequestBody Credentials credentials,
      HttpServletRequest request,
      HttpServletResponse response) {
    try {
      var authentication =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(
                  credentials.email().trim().toLowerCase(java.util.Locale.ROOT),
                  credentials.password()));
      authenticationSessions.signIn(authentication.getName(), request, response);
      return ResponseEntity.ok(userRepository.findByEmail(authentication.getName()).orElseThrow());
    } catch (AuthenticationException failure) {
      SECURITY_LOG.warn("Authentication failure for a sign-in attempt.");
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.UNAUTHORIZED, "Incorrect email or password.");
    }
  }

  @PostMapping("/password/change")
  @Transactional
  public ResponseEntity<?> changePassword(
      @Valid @RequestBody PasswordChangeRequest request,
      HttpSession session,
      HttpServletRequest servletRequest) {
    if (!sessionUser.email(session).equals(request.email().trim().toLowerCase()))
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.FORBIDDEN,
          "You can only change the password for your signed-in account.");
    Optional<User> existingUser = userRepository.findByEmail(request.email().trim().toLowerCase());
    if (existingUser.isEmpty()) {
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.UNAUTHORIZED, "Current password is incorrect.");
    }
    User user = existingUser.get();
    String storedPassword = user.getPassword();
    boolean authenticated = passwordEncoder.matches(request.currentPassword(), storedPassword);
    if (!authenticated) {
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.UNAUTHORIZED, "Current password is incorrect.");
    }
    user.setPassword(passwordEncoder.encode(request.newPassword()));
    userRepository.save(user);
    userSessions.rotateAndRevokeOthers(servletRequest, user.getId());
    return ResponseEntity.ok(Map.of("message", "Password updated."));
  }

  @PutMapping("/profile")
  @Transactional
  public ResponseEntity<?> updateProfile(
      @Valid @RequestBody ProfileUpdateRequest request,
      HttpSession session,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    User user =
        userRepository
            .findForProfileUpdate(sessionUser.id(session))
            .orElseThrow(
                () ->
                    new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED));
    if (!user.getEmail().equals(request.currentEmail().trim().toLowerCase(java.util.Locale.ROOT)))
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.FORBIDDEN,
          "You can only edit your signed-in profile.");
    String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
    if (!user.getEmail().equals(email) && userRepository.findByEmail(email).isPresent()) {
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.CONFLICT, "Email is already registered.");
    }
    String avatarImage = request.avatarImage();
    if (avatarImage != null
        && !avatarImage.isBlank()
        && !avatarImage.matches("^data:image/(png|jpeg|webp|gif);base64,[A-Za-z0-9+/=]+$")) {
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "Profile image must be a PNG, JPEG, WEBP or GIF file.");
    }
    String previousEmail = user.getEmail();
    user.setEmail(email);
    user.setDisplayName(request.displayName().trim());
    user.setJobTitle(request.jobTitle() == null ? null : request.jobTitle().trim());
    user.setBio(request.bio() == null ? null : request.bio().trim());
    user.setAvatarImage(avatarImage == null || avatarImage.isBlank() ? null : avatarImage);
    if (request.recoveryCode() != null && !request.recoveryCode().isBlank()) {
      user.setRecoveryCodeHash(passwordEncoder.encode(request.recoveryCode()));
    }
    if (!previousEmail.equals(email)) {
      userEmails.moveReferences(previousEmail, email);
      authenticationSessions.signIn(email, servletRequest, servletResponse);
    }
    return ResponseEntity.ok(userRepository.save(user));
  }

  @DeleteMapping("/account")
  @Transactional
  public ResponseEntity<?> deleteAccount(
      @Valid @RequestBody DeleteAccountRequest request, HttpSession session) {
    if (!sessionUser.email(session).equals(request.email().trim().toLowerCase()))
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.FORBIDDEN,
          "You can only delete your signed-in account.");
    Optional<User> existing = userRepository.findByEmail(request.email().trim().toLowerCase());
    if (existing.isEmpty())
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.UNAUTHORIZED, "Current password is incorrect.");
    User user = existing.get();
    String storedPassword = user.getPassword();
    boolean authenticated = passwordEncoder.matches(request.currentPassword(), storedPassword);
    if (!authenticated)
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.UNAUTHORIZED, "Current password is incorrect.");
    if (!projectRepository.findByOwnerEmail(user.getEmail()).isEmpty()) {
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.CONFLICT,
          "Delete or transfer your projects before deleting this account.");
    }
    passwordResetTokenRepository.deleteByUser(user);
    taskCommentRepository.deleteByAuthorEmail(user.getEmail());
    taskStatusHistoryRepository.anonymizeActorEmail(user.getEmail());
    notificationService.deleteForUser(user.getEmail());
    activityService.deleteForActor(user.getEmail());
    projectMemberRepository.deleteByEmailIgnoreCase(user.getEmail());
    taskRepository.unassignEverywhere(user.getEmail());
    retrospectives.anonymizeAuthor(user.getEmail());
    sharedReports.deleteByCreatedBy(user.getEmail());
    userRepository.delete(user);
    userSessions.revokeAll(user.getId());
    session.invalidate();
    return ResponseEntity.ok(Map.of("message", "Account deleted."));
  }

  @PostMapping("/password/reset/request")
  @Transactional
  public ResponseEntity<?> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
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
                      + "/?resetToken="
                      + token);
              activeMailSender.send(message);
            });
    return ResponseEntity.ok(
        Map.of("message", "If this email is registered, a reset link has been sent."));
  }

  @GetMapping("/password/reset/available")
  public ResponseEntity<?> passwordRecoveryAvailable() {
    return ResponseEntity.ok(Map.of("available", emailReady()));
  }

  @GetMapping("/google/config")
  public ResponseEntity<?> googleLoginConfiguration() {
    String clientId = activeGoogleClientId();
    return ResponseEntity.ok(Map.of("clientId", clientId));
  }

  @PostMapping("/google")
  public ResponseEntity<?> loginWithGoogle(
      @Valid @RequestBody GoogleLoginRequest request,
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
      authenticationSessions.signIn(user.getEmail(), servletRequest, servletResponse);
      return ResponseEntity.ok(user);
    }
  }

  /**
   * Unknown account, account without a recovery code and wrong code all produce the same 401 and
   * cost one BCrypt comparison, so the endpoint does not reveal which accounts exist or have a
   * code.
   */
  @PostMapping("/password/reset/recovery")
  @Transactional
  public ResponseEntity<?> resetPasswordWithRecoveryCode(
      @Valid @RequestBody RecoveryPasswordResetRequest request) {
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

  @PostMapping("/password/reset/confirm")
  @Transactional
  public ResponseEntity<?> confirmPasswordReset(
      @Valid @RequestBody PasswordResetConfirmRequest request) {
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

  private String createToken() {
    return com.sprintflowinsight.common.SecureTokens.token();
  }

  private JavaMailSender activeMailSender() {
    return mailSender;
  }

  private boolean emailReady() {
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

  private String activeGoogleClientId() {
    return googleClientId;
  }

  private String hash(String value) {
    return com.sprintflowinsight.common.SecureTokens.sha256(value);
  }
}

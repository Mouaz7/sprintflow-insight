package com.sprintflowinsight.auth;

import com.sprintflowinsight.ApiExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class AccountService {
  private static final org.slf4j.Logger SECURITY_LOG =
      org.slf4j.LoggerFactory.getLogger("security.events");
  private final UserRepository userRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final SessionUser sessionUser;
  private final UserSessions userSessions;
  private final PasswordEncoder passwordEncoder;
  private final AuthenticationManager authenticationManager;
  private final SessionAuthenticationService authenticationSessions;
  private final UserEmailService userEmails;
  private final MfaSignIn signIn;
  private final org.springframework.context.ApplicationEventPublisher events;

  public AccountService(
      UserRepository userRepository,
      PasswordResetTokenRepository passwordResetTokenRepository,
      SessionUser sessionUser,
      UserSessions userSessions,
      PasswordEncoder passwordEncoder,
      AuthenticationManager authenticationManager,
      SessionAuthenticationService authenticationSessions,
      UserEmailService userEmails,
      MfaSignIn signIn,
      org.springframework.context.ApplicationEventPublisher events) {
    this.userRepository = userRepository;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.sessionUser = sessionUser;
    this.userSessions = userSessions;
    this.passwordEncoder = passwordEncoder;
    this.authenticationManager = authenticationManager;
    this.authenticationSessions = authenticationSessions;
    this.userEmails = userEmails;
    this.signIn = signIn;
    this.events = events;
  }

  public User me(HttpSession session) {
    return userRepository
        .findByEmail(sessionUser.email(session))
        .map(user -> signIn.withWorkspaceRequirement(user, sessionUser.mfaVerified()))
        .orElseThrow(
            () ->
                new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED));
  }

  public ResponseEntity<?> register(
      Credentials credentials, HttpServletRequest request, HttpServletResponse response) {
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

  public ResponseEntity<?> login(
      Credentials credentials, HttpServletRequest request, HttpServletResponse response) {
    try {
      var authentication =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(
                  credentials.email().trim().toLowerCase(java.util.Locale.ROOT),
                  credentials.password()));
      User user = userRepository.findByEmail(authentication.getName()).orElseThrow();
      return signIn.afterPrimary(user, request, response);
    } catch (AuthenticationException failure) {
      SECURITY_LOG.warn("Authentication failure for a sign-in attempt.");
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.UNAUTHORIZED, "Incorrect email or password.");
    }
  }

  @Transactional
  public ResponseEntity<?> changePassword(
      PasswordChangeRequest request, HttpSession session, HttpServletRequest servletRequest) {
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

  @Transactional
  public ResponseEntity<?> updateProfile(
      ProfileUpdateRequest request,
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
      authenticationSessions.signIn(
          email, servletRequest, servletResponse, sessionUser.mfaVerified());
    }
    return ResponseEntity.ok(
        signIn.withWorkspaceRequirement(userRepository.save(user), sessionUser.mfaVerified()));
  }

  @Transactional
  public ResponseEntity<?> deleteAccount(DeleteAccountRequest request, HttpSession session) {
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
    events.publishEvent(new AccountDeleting(user.getId(), user.getEmail()));
    passwordResetTokenRepository.deleteByUser(user);
    userRepository.delete(user);
    userSessions.revokeAll(user.getId());
    session.invalidate();
    return ResponseEntity.ok(Map.of("message", "Account deleted."));
  }
}

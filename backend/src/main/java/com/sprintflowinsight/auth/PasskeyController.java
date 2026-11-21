package com.sprintflowinsight.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/auth/passkeys")
class PasskeyController {
  record Registration(@NotBlank @Size(max = 80) String label, @NotNull JsonNode credential) {
    @Override
    public String toString() {
      return "PasskeyRegistration[credential=redacted]";
    }
  }

  record Assertion(@NotNull JsonNode credential) {
    @Override
    public String toString() {
      return "PasskeyAssertion[credential=redacted]";
    }
  }

  private final PasskeyService passkeys;
  private final SessionAuthenticationService sessions;

  PasskeyController(PasskeyService passkeys, SessionAuthenticationService sessions) {
    this.passkeys = passkeys;
    this.sessions = sessions;
  }

  @GetMapping("/config")
  PasskeyService.Configuration configuration() {
    return passkeys.configuration();
  }

  @GetMapping
  List<PasskeyService.Credential> list(HttpSession session) {
    return passkeys.list(session);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void remove(@PathVariable String id, HttpSession session) {
    passkeys.delete(id, session);
  }

  @PostMapping("/registration/options")
  ResponseEntity<JsonNode> registrationOptions(HttpSession session) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(passkeys.registrationOptions(session));
  }

  @PostMapping("/registration/complete")
  @ResponseStatus(HttpStatus.CREATED)
  PasskeyService.Credential register(
      @Valid @RequestBody Registration request, HttpSession session) {
    return passkeys.register(request.label(), request.credential(), session);
  }

  @PostMapping("/login/options")
  ResponseEntity<JsonNode> loginOptions(HttpSession session) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(passkeys.loginOptions(session));
  }

  @PostMapping("/login/complete")
  ResponseEntity<User> login(
      @Valid @RequestBody Assertion attempt,
      HttpServletRequest servlet,
      HttpServletResponse response) {
    User user = passkeys.authenticate(attempt.credential(), servlet.getSession(false));
    sessions.signIn(user.getEmail(), servlet, response, true);
    return ResponseEntity.ok(user);
  }
}

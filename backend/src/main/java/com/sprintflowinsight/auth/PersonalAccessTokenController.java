package com.sprintflowinsight.auth;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/tokens")
class PersonalAccessTokenController {
  private final PersonalAccessTokens tokens;

  PersonalAccessTokenController(PersonalAccessTokens tokens) {
    this.tokens = tokens;
  }

  @GetMapping
  List<PersonalAccessTokens.Token> list(HttpSession session) {
    return tokens.list(session);
  }

  @PostMapping
  ResponseEntity<PersonalAccessTokens.Issued> issue(
      @Valid @RequestBody PersonalAccessTokens.Draft draft, HttpSession session) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(tokens.issue(draft, session));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void revoke(@PathVariable long id, HttpSession session) {
    tokens.revoke(id, session);
  }
}

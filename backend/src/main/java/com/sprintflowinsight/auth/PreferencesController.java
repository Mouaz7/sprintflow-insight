package com.sprintflowinsight.auth;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class PreferencesController {
  private final PreferencesService service;

  public PreferencesController(PreferencesService service) {
    this.service = service;
  }

  @GetMapping("/preferences")
  public UserPreferences get(HttpSession session) {
    return service.get(session);
  }

  @PutMapping("/preferences")
  public UserPreferences save(@Valid @RequestBody PreferencesRequest request, HttpSession session) {
    return service.save(request, session);
  }
}

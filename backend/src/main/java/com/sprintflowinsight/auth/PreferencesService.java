package com.sprintflowinsight.auth;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.common.error.ErrorCode;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Validator;
import java.util.List;

@org.springframework.stereotype.Service
public class PreferencesService {
  static final int MAX_SAVED_FILTERS = 30;

  private final UserPreferencesRepository preferences;
  private final SessionUser sessionUser;
  private final ObjectMapper json;
  private final Validator validator;

  public PreferencesService(
      UserPreferencesRepository preferences,
      SessionUser sessionUser,
      ObjectMapper json,
      Validator validator) {
    this.preferences = preferences;
    this.sessionUser = sessionUser;
    this.json = json;
    this.validator = validator;
  }

  public UserPreferences get(HttpSession session) {
    Long id = sessionUser.id(session);
    return preferences
        .findById(id)
        .orElseGet(
            () -> {
              var value = new UserPreferences();
              value.setUserId(id);
              return value;
            });
  }

  public UserPreferences save(PreferencesRequest request, HttpSession session) {
    sessionUser.email(session); // the account must still exist
    var value = get(session);
    if (request.language() != null) value.setLanguage(request.language());
    if (request.theme() != null) value.setTheme(request.theme());
    if (request.notifications() != null) value.setNotifications(request.notifications());
    if (request.onboardingCompleted() != null)
      value.setOnboardingCompleted(request.onboardingCompleted());
    if (request.savedFilters() != null) value.setSavedFilters(validFilters(request.savedFilters()));
    return preferences.save(value);
  }

  private String validFilters(String text) {
    List<PreferencesRequest.SavedFilter> filters;
    try {
      filters = json.readValue(text, new TypeReference<>() {});
    } catch (Exception malformed) {
      throw invalid("Saved filters must be a JSON array.");
    }
    if (filters == null || filters.size() > MAX_SAVED_FILTERS)
      throw invalid("You can save at most " + MAX_SAVED_FILTERS + " filters.");
    for (var filter : filters) {
      if (filter == null) throw invalid("Saved filters must be objects.");
      var violations = validator.validate(filter);
      if (!violations.isEmpty()) throw invalid(violations.iterator().next().getMessage());
    }
    return text;
  }

  private static ApiException invalid(String message) {
    return new ApiException(ErrorCode.VALIDATION_FAILED, message);
  }
}

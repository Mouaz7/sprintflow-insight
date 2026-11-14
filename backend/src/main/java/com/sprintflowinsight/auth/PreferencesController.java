package com.sprintflowinsight.auth;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.Map;
import java.util.Set;
@RestController @RequestMapping("/api/auth")
public class PreferencesController {
 private final UserPreferencesRepository preferences; private final UserRepository users; private final SessionUser sessionUser; private final ObjectMapper json;
 public PreferencesController(UserPreferencesRepository preferences,UserRepository users,SessionUser sessionUser,ObjectMapper json){this.preferences=preferences;this.users=users;this.sessionUser=sessionUser;this.json=json;}
 @GetMapping("/preferences") public UserPreferences get(HttpSession session){Long id=users.findByEmail(sessionUser.email(session)).orElseThrow().getId();return preferences.findById(id).orElseGet(()->{var value=new UserPreferences();value.setUserId(id);return value;});}
 @PutMapping("/preferences") public UserPreferences save(@RequestBody Map<String,Object> request,HttpSession session){var value=get(session);
  if(request.containsKey("language")){String language=String.valueOf(request.get("language"));if(!Set.of("EN","SV").contains(language))throw invalid();value.setLanguage(language);}
  if(request.containsKey("theme")){String theme=String.valueOf(request.get("theme"));if(!Set.of("light","dark").contains(theme))throw invalid();value.setTheme(theme);}
  if(request.containsKey("notifications")){if(!(request.get("notifications") instanceof Boolean))throw invalid();value.setNotifications((Boolean)request.get("notifications"));}
  if(request.containsKey("onboardingCompleted")){if(!(request.get("onboardingCompleted") instanceof Boolean))throw invalid();value.setOnboardingCompleted((Boolean)request.get("onboardingCompleted"));}
  if(request.containsKey("savedFilters")){String filters=String.valueOf(request.get("savedFilters"));try{var node=json.readTree(filters);if(!node.isArray()||node.size()>30||filters.length()>30000)throw invalid();for(var filter:node){if(!filter.isObject()||!filter.hasNonNull("id")||!filter.hasNonNull("name")||filter.get("name").asText().length()>80)throw invalid();}}catch(ResponseStatusException ex){throw ex;}catch(Exception ex){throw invalid();}value.setSavedFilters(filters);}
  return preferences.save(value);
 }
 private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid preference settings.");}
}

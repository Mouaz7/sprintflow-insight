package com.sprintflowinsight.automation;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/automation")
class AutomationController {
  private final AutomationRules rules;

  AutomationController(AutomationRules rules) {
    this.rules = rules;
  }

  @GetMapping("/rules")
  List<AutomationRules.Rule> rules(@PathVariable long projectId, HttpSession session) {
    return rules.list(projectId, session);
  }

  @GetMapping("/runs")
  List<AutomationRules.Run> runs(@PathVariable long projectId, HttpSession session) {
    return rules.recentRuns(projectId, session);
  }

  @PostMapping("/rules")
  @ResponseStatus(HttpStatus.CREATED)
  AutomationRules.Rule create(
      @PathVariable long projectId,
      @Valid @RequestBody AutomationRules.Draft draft,
      HttpSession session) {
    return rules.create(projectId, draft, session);
  }

  @PutMapping("/rules/{ruleId}")
  AutomationRules.Rule replace(
      @PathVariable long projectId,
      @PathVariable long ruleId,
      @Valid @RequestBody AutomationRules.Draft draft,
      @RequestHeader(value = "If-Match", required = false) String version,
      HttpSession session) {
    return rules.replace(projectId, ruleId, draft, version, session);
  }

  @DeleteMapping("/rules/{ruleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(
      @PathVariable long projectId,
      @PathVariable long ruleId,
      @RequestHeader(value = "If-Match", required = false) String version,
      HttpSession session) {
    rules.delete(projectId, ruleId, version, session);
  }
}

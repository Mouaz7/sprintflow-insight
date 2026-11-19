package com.sprintflowinsight.workspace.customfield;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class CustomFieldController {
  private final CustomFieldService service;

  public CustomFieldController(CustomFieldService service) {
    this.service = service;
  }

  @GetMapping("/api/projects/{projectId}/custom-fields")
  public List<CustomFieldViews.Definition> list(@PathVariable Long projectId, HttpSession session) {
    return service.definitions(projectId, session);
  }

  @PostMapping("/api/projects/{projectId}/custom-fields")
  public CustomFieldViews.Definition create(
      @PathVariable Long projectId,
      @Valid @RequestBody FieldDefinitionRequest request,
      HttpSession session) {
    return service.create(projectId, request, session);
  }

  @PutMapping("/api/projects/{projectId}/custom-fields/{fieldId}")
  public CustomFieldViews.Definition update(
      @PathVariable Long projectId,
      @PathVariable Long fieldId,
      @Valid @RequestBody FieldDefinitionRequest request,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    return service.update(projectId, fieldId, request, ifMatch, session);
  }

  @DeleteMapping("/api/projects/{projectId}/custom-fields/{fieldId}")
  public ResponseEntity<Void> delete(
      @PathVariable Long projectId,
      @PathVariable Long fieldId,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    service.delete(projectId, fieldId, ifMatch, session);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/projects/{projectId}/custom-fields/matches")
  public CustomFieldViews.Matches matches(
      @PathVariable Long projectId,
      @Valid @ModelAttribute FieldCondition condition,
      HttpSession session) {
    return service.matching(projectId, condition, session);
  }

  @GetMapping("/api/tasks/{taskId}/custom-fields")
  public ResponseEntity<CustomFieldViews.TaskValues> taskValues(
      @PathVariable Long taskId, HttpSession session) {
    var snapshot = service.taskValues(taskId, session);
    return ResponseEntity.ok().eTag(snapshot.version().toString()).body(snapshot);
  }

  @PutMapping("/api/tasks/{taskId}/custom-fields")
  public ResponseEntity<CustomFieldViews.TaskValues> replace(
      @PathVariable Long taskId,
      @Valid @RequestBody FieldValuesRequest request,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    var snapshot = service.replaceValues(taskId, request, ifMatch, session);
    return ResponseEntity.ok().eTag(snapshot.version().toString()).body(snapshot);
  }
}

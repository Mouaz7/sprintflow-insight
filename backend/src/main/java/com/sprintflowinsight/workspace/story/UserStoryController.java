package com.sprintflowinsight.workspace.story;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stories")
public class UserStoryController {
  private final UserStoryService service;

  public UserStoryController(UserStoryService service) {
    this.service = service;
  }

  @GetMapping("/project/{projectId}")
  public List<UserStory> getStoriesByProject(@PathVariable Long projectId, HttpSession session) {
    return service.getStoriesByProject(projectId, session);
  }

  @PutMapping("/project/{projectId}/order")
  public List<UserStory> reorder(
      @PathVariable Long projectId, @Valid @RequestBody OrderRequest request, HttpSession session) {
    return service.reorder(projectId, request, session);
  }

  @GetMapping("/{id}")
  public UserStory getUserStory(@PathVariable Long id, HttpSession session) {
    return service.getUserStory(id, session);
  }

  @PostMapping
  public ResponseEntity<?> createUserStory(
      @Valid @RequestBody StoryRequest request, HttpSession session) {
    return service.createUserStory(request, session);
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> updateUserStory(
      @PathVariable("id") Long id,
      @Valid @RequestBody StoryRequest request,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    return service.updateUserStory(id, request, ifMatch, session);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteUserStory(
      @PathVariable("id") Long id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    return service.deleteUserStory(id, ifMatch, session);
  }
}

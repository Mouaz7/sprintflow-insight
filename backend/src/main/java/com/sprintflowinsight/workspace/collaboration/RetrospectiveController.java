package com.sprintflowinsight.workspace.collaboration;

import com.sprintflowinsight.workspace.project.ProjectAccessService;
import com.sprintflowinsight.workspace.sprint.SprintRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/projects/{projectId}/retrospective")
public class RetrospectiveController {
  private final RetrospectiveRepository entries;
  private final SprintRepository sprints;
  private final ProjectAccessService access;

  public RetrospectiveController(
      RetrospectiveRepository entries, SprintRepository sprints, ProjectAccessService access) {
    this.entries = entries;
    this.sprints = sprints;
    this.access = access;
  }

  @GetMapping
  public List<RetrospectiveEntry> list(
      @PathVariable Long projectId, @RequestParam Long sprintId, HttpSession session) {
    access.read(projectId, session);
    sprint(projectId, sprintId);
    return entries.findByProjectIdAndSprintIdOrderByCreatedAtAsc(projectId, sprintId);
  }

  @PostMapping
  public RetrospectiveEntry add(
      @PathVariable Long projectId, @Valid @RequestBody EntryRequest request, HttpSession session) {
    access.write(projectId, session);
    sprint(projectId, request.sprintId());
    validateCategory(request.category());
    var entry = new RetrospectiveEntry();
    entry.setProjectId(projectId);
    entry.setSprintId(request.sprintId());
    entry.setAuthorEmail(access.email(session));
    entry.setCategory(request.category());
    entry.setMessage(request.message().trim());
    entry.setCreatedAt(Instant.now());
    return entries.save(entry);
  }

  @PutMapping("/{id}")
  public RetrospectiveEntry update(
      @PathVariable Long projectId,
      @PathVariable Long id,
      @Valid @RequestBody EntryRequest request,
      HttpSession session) {
    var entry = editable(projectId, id, session);
    if (!entry.getSprintId().equals(request.sprintId()))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "A retrospective entry cannot move to another sprint.");
    if (request.version() != null && !request.version().equals(entry.getVersion()))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "This item was changed by someone else. Refresh and try again.");
    validateCategory(request.category());
    entry.setCategory(request.category());
    entry.setMessage(request.message().trim());
    return entries.save(entry);
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable Long projectId, @PathVariable Long id, HttpSession session) {
    entries.delete(editable(projectId, id, session));
  }

  private RetrospectiveEntry editable(Long projectId, Long id, HttpSession session) {
    access.write(projectId, session);
    var entry =
        entries
            .findById(id)
            .filter(e -> projectId.equals(e.getProjectId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    sprint(projectId, entry.getSprintId());
    String role = access.role(projectId, session);
    if (!entry.getAuthorEmail().equalsIgnoreCase(access.email(session))
        && !Set.of("Owner", "Admin").contains(role))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "You can only edit your own retrospective entries.");
    return entry;
  }

  private void sprint(Long projectId, Long sprintId) {
    if (sprintId == null
        || sprints
            .findById(sprintId)
            .filter(s -> projectId.equals(s.getProjectId()) && s.getTrashedAt() == null)
            .isEmpty())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a sprint in this project.");
  }

  private void validateCategory(String value) {
    if (!Set.of("well", "improve", "actions").contains(value))
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Choose a valid retrospective category.");
  }

  public record EntryRequest(
      Long sprintId,
      @NotBlank String category,
      @NotBlank @Size(max = 1000) String message,
      Long version) {}
}

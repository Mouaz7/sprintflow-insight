package com.sprintflowinsight.project;

import com.sprintflowinsight.common.error.ApiException;

import com.sprintflowinsight.ApiExceptionHandler;
import com.sprintflowinsight.auth.UserRepository;
import com.sprintflowinsight.service.ActivityService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
  private final ProjectRepository projects;
  private final ProjectMemberRepository members;
  private final ProjectAccessService access;
  private final UserRepository users;
  private final ProjectDeletionService deletion;
  private final ActivityService activity;
  private final com.sprintflowinsight.repository.TaskRepository tasks;
  private final ProjectFactory factory;

  public ProjectController(
      ProjectRepository projects,
      ProjectMemberRepository members,
      ProjectAccessService access,
      UserRepository users,
      ProjectDeletionService deletion,
      ActivityService activity,
      com.sprintflowinsight.repository.TaskRepository tasks,
      ProjectFactory factory) {
    this.factory = factory;
    this.projects = projects;
    this.members = members;
    this.access = access;
    this.users = users;
    this.deletion = deletion;
    this.activity = activity;
    this.tasks = tasks;
  }

  @GetMapping
  public List<Project> getAllProjects(
      HttpSession session,
      @RequestParam(required = false) Integer page,
      @RequestParam(defaultValue = "100") int size) {
    return projects.findVisibleTo(
        access.email(session), com.sprintflowinsight.OptionalPage.of(page, size));
  }

  @PostMapping
  @Transactional
  public ResponseEntity<?> createProject(
      @Valid @RequestBody ProjectRequest request, HttpSession session) {
    String email = access.email(session);
    return ResponseEntity.ok(factory.create(request.trimmedName(), request.description(), email));
  }

  @GetMapping("/{id}")
  public Project getProject(@PathVariable Long id, HttpSession session) {
    access.read(id, session);
    return projects.findById(id).orElseThrow();
  }

  @PutMapping("/{id}")
  @Transactional
  public ResponseEntity<?> updateProject(
      @PathVariable Long id,
      @Valid @RequestBody ProjectRequest details,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      HttpSession session) {
    access.require(id, session, AccessContext.Need.MANAGE);
    if (projects
        .findByName(details.trimmedName())
        .filter(p -> !p.getId().equals(id))
        .isPresent())
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.CONFLICT, "A project with this name already exists.");
    return projects
        .findById(id)
        .map(
            p -> {
              com.sprintflowinsight.VersionPrecondition.require(ifMatch, p.getVersion());
              p.setName(details.trimmedName());
              p.setDescription(details.description());
              Project saved = projects.save(p);
              activity.record(
                  id,
                  "PROJECT_UPDATED",
                  "Updated project “" + saved.getName() + "”.",
                  access.email(session));
              return ResponseEntity.ok(saved);
            })
        .orElseThrow(() -> ApiException.notFound("Project not found."));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> deleteProject(@PathVariable Long id, HttpSession session) {
    deletion.delete(id, session);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/{id}/role")
  public ResponseEntity<?> currentRole(@PathVariable Long id, HttpSession session) {
    return ResponseEntity.ok(Map.of("role", access.role(id, session)));
  }

  @GetMapping("/{id}/members")
  public ResponseEntity<?> listMembers(@PathVariable Long id, HttpSession session) {
    access.read(id, session);
    return ResponseEntity.ok(members.findByProjectId(id));
  }

  @PostMapping("/{id}/members")
  @Transactional
  public ResponseEntity<?> addMember(
      @PathVariable Long id, @Valid @RequestBody MemberRequest request, HttpSession session) {
    AccessContext caller = access.require(id, session, AccessContext.Need.MANAGE);
    String email = request.email().trim().toLowerCase();
    if (users.findByEmail(email).isEmpty())
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "Create an account for this email before inviting it.");
    if (!Set.of("Admin", "Member", "Viewer").contains(request.role()))
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "Role must be Admin, Member, or Viewer. Transfer project ownership to appoint a new"
              + " owner.");
    if (projects.findById(id).orElseThrow().getOwnerEmail().equalsIgnoreCase(email))
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.CONFLICT,
          "Transfer ownership before changing the owner role.");
    ProjectMember member =
        members.findByProjectIdAndEmailIgnoreCase(id, email).orElseGet(ProjectMember::new);
    requireMayManage(caller, member.getId() == null ? null : member.getRole(), email);
    if (caller.role() != ProjectRole.OWNER && ProjectRole.fromLabel(request.role()) == ProjectRole.ADMIN)
      throw ApiException.forbidden("Only the project owner can appoint admins.");
    member.setProjectId(id);
    member.setEmail(email);
    member.setRole(request.role());
    ProjectMember saved = members.save(member);
    activity.record(
        id, "MEMBER_UPDATED", "Updated project role for " + email + ".", access.email(session));
    return ResponseEntity.ok(saved);
  }

  @DeleteMapping("/{id}/members/{email}")
  @Transactional
  public ResponseEntity<?> removeMember(
      @PathVariable Long id, @PathVariable String email, HttpSession session) {
    AccessContext caller = access.require(id, session, AccessContext.Need.MANAGE);
    Project p = projects.lockById(id).orElseThrow();
    if (p.getOwnerEmail().equalsIgnoreCase(email))
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.CONFLICT,
          "Transfer ownership before removing the owner.");
    var existing = members.findByProjectIdAndEmailIgnoreCase(id, email);
    if (existing.isEmpty()) return ResponseEntity.noContent().build();
    requireMayManage(caller, existing.get().getRole(), email);
    tasks.unassignInProject(id, email);
    members.deleteByProjectIdAndEmailIgnoreCase(id, email);
    activity.record(
        id, "MEMBER_REMOVED", "Removed project member " + email + ".", access.email(session));
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{id}/owner")
  @Transactional
  public ResponseEntity<?> transferOwnership(
      @PathVariable Long id, @Valid @RequestBody OwnershipRequest request, HttpSession session) {
    access.owner(id, session);
    String nextOwner = request.email().trim().toLowerCase();
    if (users.findByEmail(nextOwner).isEmpty())
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.BAD_REQUEST, "The new owner must have an account.");
    Project project = projects.findById(id).orElseThrow();
    if (project.getOwnerEmail().equalsIgnoreCase(nextOwner)) return ResponseEntity.ok(project);
    ProjectMember target = members.findByProjectIdAndEmailIgnoreCase(id, nextOwner).orElse(null);
    if (target == null)
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "The new owner must already be a project member.");
    String previousOwner = project.getOwnerEmail();
    ProjectMember previous =
        members.findByProjectIdAndEmailIgnoreCase(id, previousOwner).orElseGet(ProjectMember::new);
    previous.setProjectId(id);
    previous.setEmail(previousOwner);
    previous.setRole("Member");
    target.setRole("Owner");
    members.save(previous);
    members.save(target);
    project.setOwnerEmail(nextOwner);
    Project saved = projects.save(project);
    activity.record(
        id,
        "OWNERSHIP_TRANSFERRED",
        "Transferred project ownership to " + nextOwner + ".",
        access.email(session));
    return ResponseEntity.ok(saved);
  }

  /**
   * Admins manage members and viewers. Only the owner may change or remove another admin; an
   * admin may still step down or leave themselves.
   */
  private static void requireMayManage(AccessContext caller, String targetRole, String targetEmail) {
    if (caller.role() == ProjectRole.OWNER || targetRole == null) return;
    if (ProjectRole.fromLabel(targetRole) == ProjectRole.ADMIN
        && !caller.email().equalsIgnoreCase(targetEmail))
      throw ApiException.forbidden("Only the project owner can change or remove another admin.");
  }

  public record MemberRequest(
      @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String email,
      @jakarta.validation.constraints.NotBlank String role) {}

  public record OwnershipRequest(
      @jakarta.validation.constraints.NotBlank(message = "Choose a project member to receive ownership.")
          @jakarta.validation.constraints.Email(message = "Enter a valid email address.")
          String email) {}
}

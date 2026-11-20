package com.sprintflowinsight.workspace.project;

import com.sprintflowinsight.ApiExceptionHandler;
import com.sprintflowinsight.auth.UserRepository;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.activity.ActivityService;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class ProjectService {
  private final ProjectRepository projects;
  private final ProjectMemberRepository members;
  private final ProjectAccessService access;
  private final UserRepository users;
  private final ProjectDeletionService deletion;
  private final ActivityService activity;
  private final com.sprintflowinsight.workspace.task.TaskRepository tasks;
  private final ProjectFactory factory;
  private final ProjectAdministrativeAccess admin;

  public ProjectService(
      ProjectRepository projects,
      ProjectMemberRepository members,
      ProjectAccessService access,
      UserRepository users,
      ProjectDeletionService deletion,
      ActivityService activity,
      com.sprintflowinsight.workspace.task.TaskRepository tasks,
      ProjectFactory factory,
      ProjectAdministrativeAccess admin) {
    this.admin = admin;
    this.factory = factory;
    this.projects = projects;
    this.members = members;
    this.access = access;
    this.users = users;
    this.deletion = deletion;
    this.activity = activity;
    this.tasks = tasks;
  }

  public List<Project> getAllProjects(HttpSession session, Integer page, int size) {
    return projects.findVisibleTo(
        access.userId(session), com.sprintflowinsight.OptionalPage.of(page, size));
  }

  /** Runs in its own transaction, retried when a concurrent creation took the derived key. */
  public ResponseEntity<?> createProject(ProjectRequest request, HttpSession session) {
    String email = access.email(session);
    String key = request.normalizedKey();
    return ResponseEntity.ok(
        factory.inTransactionRetryingDerivedKey(
            key,
            () -> factory.create(request.trimmedName(), request.description(), email, key, null)));
  }

  public Project getProject(Long id, HttpSession session) {
    access.read(id, session);
    return projects.findById(id).orElseThrow();
  }

  @Transactional
  public ResponseEntity<?> updateProject(
      Long id, ProjectRequest details, String ifMatch, HttpSession session) {
    AccessContext caller = admin.lock(id, session, AccessContext.Need.MANAGE);
    if (projects.findByName(details.trimmedName()).filter(p -> !p.getId().equals(id)).isPresent())
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.CONFLICT, "A project with this name already exists.");
    return projects
        .findById(id)
        .map(
            p -> {
              com.sprintflowinsight.VersionPrecondition.require(ifMatch, p.getVersion());
              String key = details.normalizedKey();
              if (key != null && !key.equals(p.getKey())) p.setKey(factory.requireFreeKey(key));
              p.setName(details.trimmedName());
              p.setDescription(details.description());
              Project saved = projects.save(p);
              admin.record(caller, "PROJECT_UPDATED", "project", id, Map.of());
              activity.record(
                  id,
                  "PROJECT_UPDATED",
                  "Updated project “" + saved.getName() + "”.",
                  access.email(session));
              return ResponseEntity.ok(saved);
            })
        .orElseThrow(() -> ApiException.notFound("Project not found."));
  }

  public ResponseEntity<?> deleteProject(Long id, HttpSession session) {
    deletion.delete(id, session);
    return ResponseEntity.noContent().build();
  }

  public ResponseEntity<?> currentRole(Long id, HttpSession session) {
    return ResponseEntity.ok(Map.of("role", access.role(id, session)));
  }

  public ResponseEntity<?> listMembers(Long id, HttpSession session) {
    access.read(id, session);
    return ResponseEntity.ok(members.findByProjectId(id));
  }

  @Transactional
  public ResponseEntity<?> addMember(Long id, MemberRequest request, HttpSession session) {
    AccessContext caller = admin.lock(id, session, AccessContext.Need.MANAGE);
    String email = request.email().trim().toLowerCase();
    var account = users.findByEmail(email);
    if (account.isEmpty())
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "Create an account for this email before inviting it.");
    Long userId = account.get().getId();
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
        members.findByProjectIdAndUserId(id, userId).orElseGet(ProjectMember::new);
    requireMayManage(caller, member.getId() == null ? null : member.getRole(), email);
    if (caller.role() != ProjectRole.OWNER
        && ProjectRole.fromLabel(request.role()) == ProjectRole.ADMIN)
      throw ApiException.forbidden("Only the project owner can appoint admins.");
    member.setProjectId(id);
    member.setUserId(userId);
    member.setEmail(email);
    member.setRole(request.role());
    ProjectMember saved = members.save(member);
    admin.record(caller, "MEMBER_UPDATED", "member", saved.getId(), Map.of("role", request.role()));
    activity.record(
        id, "MEMBER_UPDATED", "Updated project role for " + email + ".", access.email(session));
    return ResponseEntity.ok(saved);
  }

  @Transactional
  public ResponseEntity<?> removeMember(Long id, String email, HttpSession session) {
    AccessContext caller = admin.lock(id, session, AccessContext.Need.MANAGE);
    Project p = projects.lockById(id).orElseThrow();
    if (p.getOwnerEmail().equalsIgnoreCase(email))
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.CONFLICT,
          "Transfer ownership before removing the owner.");
    var existing =
        users
            .findByEmail(email.trim().toLowerCase(Locale.ROOT))
            .flatMap(account -> members.findByProjectIdAndUserId(id, account.getId()));
    if (existing.isEmpty()) return ResponseEntity.noContent().build();
    requireMayManage(caller, existing.get().getRole(), email);
    tasks.unassignInProject(id, existing.get().getEmail());
    members.delete(existing.get());
    admin.record(caller, "MEMBER_REMOVED", "member", existing.get().getId(), Map.of());
    activity.record(
        id, "MEMBER_REMOVED", "Removed project member " + email + ".", access.email(session));
    return ResponseEntity.noContent().build();
  }

  @Transactional
  public ResponseEntity<?> transferOwnership(
      Long id, OwnershipRequest request, HttpSession session) {
    AccessContext caller = admin.lock(id, session, AccessContext.Need.OWNER);
    String nextOwner = request.email().trim().toLowerCase();
    var nextAccount = users.findByEmail(nextOwner);
    if (nextAccount.isEmpty())
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.BAD_REQUEST, "The new owner must have an account.");
    Project project = projects.findById(id).orElseThrow();
    if (project.getOwnerEmail().equalsIgnoreCase(nextOwner)) return ResponseEntity.ok(project);
    ProjectMember target =
        members.findByProjectIdAndUserId(id, nextAccount.get().getId()).orElse(null);
    if (target == null)
      return ApiExceptionHandler.error(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "The new owner must already be a project member.");
    String previousOwner = project.getOwnerEmail();
    Long previousOwnerId = access.userId(session);
    ProjectMember previous =
        members.findByProjectIdAndUserId(id, previousOwnerId).orElseGet(ProjectMember::new);
    previous.setProjectId(id);
    previous.setUserId(previousOwnerId);
    previous.setEmail(previousOwner);
    previous.setRole("Member");
    target.setRole("Owner");
    members.save(previous);
    members.save(target);
    project.setOwnerEmail(nextOwner);
    Project saved = projects.save(project);
    admin.record(
        caller,
        "OWNERSHIP_TRANSFERRED",
        "project",
        id,
        Map.of("newOwnerId", nextAccount.get().getId()));
    activity.record(
        id,
        "OWNERSHIP_TRANSFERRED",
        "Transferred project ownership to " + nextOwner + ".",
        access.email(session));
    return ResponseEntity.ok(saved);
  }

  /**
   * Admins manage members and viewers. Only the owner may change or remove another admin; an admin
   * may still step down or leave themselves.
   */
  private static void requireMayManage(
      AccessContext caller, String targetRole, String targetEmail) {
    if (caller.role() == ProjectRole.OWNER || targetRole == null) return;
    if (ProjectRole.fromLabel(targetRole) == ProjectRole.ADMIN
        && !caller.email().equalsIgnoreCase(targetEmail))
      throw ApiException.forbidden("Only the project owner can change or remove another admin.");
  }
}

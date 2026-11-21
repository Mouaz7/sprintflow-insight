package com.sprintflowinsight.workspace.organization;

import com.sprintflowinsight.workspace.project.Project;
import com.sprintflowinsight.workspace.project.ProjectRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/organizations")
class OrganizationController {
  private static final String ROLE = "Admin|Member|Viewer";
  private final OrganizationService organizations;
  private final OrganizationTeams teams;
  private final OrganizationInvitations invitations;
  private final OrganizationTemplates templates;

  OrganizationController(
      OrganizationService organizations,
      OrganizationTeams teams,
      OrganizationInvitations invitations,
      OrganizationTemplates templates) {
    this.organizations = organizations;
    this.teams = teams;
    this.invitations = invitations;
    this.templates = templates;
  }

  record CreateOrganization(
      @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,78}[a-z0-9]") String slug,
      @NotBlank @Size(max = 120) String name) {}

  record ChangeRole(@NotBlank @Pattern(regexp = ROLE) String role) {}

  record Ownership(@Positive long userId) {}

  record Name(@NotBlank @Size(max = 120) String name) {}

  record Invite(
      @NotBlank @Email @Size(max = 255) String email,
      @NotBlank @Pattern(regexp = ROLE) String role) {}

  record AcceptInvitation(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token) {
    @Override
    public String toString() {
      return "AcceptInvitation[token=redacted]";
    }
  }

  record Template(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Pattern(regexp = "Blank|Scrum|Kanban") String kind,
      @Size(max = 255) String description) {}

  @GetMapping
  List<OrganizationService.Organization> list(HttpSession session) {
    return organizations.list(session);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  OrganizationService.Organization create(
      @Valid @RequestBody CreateOrganization request, HttpSession session) {
    return organizations.create(request.slug(), request.name(), session);
  }

  @GetMapping("/{slug}/projects")
  List<Project> projects(@PathVariable String slug, HttpSession session) {
    return organizations.listProjects(slug, session);
  }

  @PostMapping("/{slug}/projects")
  @ResponseStatus(HttpStatus.CREATED)
  Project createProject(
      @PathVariable String slug, @Valid @RequestBody ProjectRequest request, HttpSession session) {
    return organizations.createProject(slug, request, session);
  }

  @GetMapping("/{slug}/members")
  List<OrganizationService.Member> members(@PathVariable String slug, HttpSession session) {
    return organizations.members(slug, session);
  }

  @PutMapping("/{slug}/members/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void changeRole(
      @PathVariable String slug,
      @PathVariable long id,
      @Valid @RequestBody ChangeRole request,
      HttpSession session) {
    organizations.changeRole(slug, id, request.role(), session);
  }

  @DeleteMapping("/{slug}/members/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void removeMember(@PathVariable String slug, @PathVariable long id, HttpSession session) {
    organizations.removeMember(slug, id, session);
  }

  @PutMapping("/{slug}/ownership")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void transferOwnership(
      @PathVariable String slug, @Valid @RequestBody Ownership request, HttpSession session) {
    organizations.transferOwnership(slug, request.userId(), session);
  }

  @GetMapping("/{slug}/teams")
  List<OrganizationTeams.Team> teams(@PathVariable String slug, HttpSession session) {
    return teams.list(slug, session);
  }

  @PostMapping("/{slug}/teams")
  @ResponseStatus(HttpStatus.CREATED)
  OrganizationTeams.Team createTeam(
      @PathVariable String slug, @Valid @RequestBody Name request, HttpSession session) {
    return teams.create(slug, request.name(), session);
  }

  @PutMapping("/{slug}/teams/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void renameTeam(
      @PathVariable String slug,
      @PathVariable long id,
      @Valid @RequestBody Name request,
      HttpSession session) {
    teams.rename(slug, id, request.name(), session);
  }

  @DeleteMapping("/{slug}/teams/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteTeam(@PathVariable String slug, @PathVariable long id, HttpSession session) {
    teams.delete(slug, id, session);
  }

  @GetMapping("/{slug}/teams/{id}/members")
  List<OrganizationService.Member> teamMembers(
      @PathVariable String slug, @PathVariable long id, HttpSession session) {
    return teams.members(slug, id, session);
  }

  @PutMapping("/{slug}/teams/{id}/members/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void addTeamMember(
      @PathVariable String slug,
      @PathVariable long id,
      @PathVariable long userId,
      HttpSession session) {
    teams.addMember(slug, id, userId, session);
  }

  @DeleteMapping("/{slug}/teams/{id}/members/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void removeTeamMember(
      @PathVariable String slug,
      @PathVariable long id,
      @PathVariable long userId,
      HttpSession session) {
    teams.removeMember(slug, id, userId, session);
  }

  @GetMapping("/{slug}/invites")
  List<OrganizationInvitations.Pending> invites(@PathVariable String slug, HttpSession session) {
    return invitations.list(slug, session);
  }

  @PostMapping("/{slug}/invites")
  ResponseEntity<OrganizationInvitations.Issued> issueInvite(
      @PathVariable String slug, @Valid @RequestBody Invite request, HttpSession session) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(invitations.issue(slug, request.email(), request.role(), session));
  }

  @DeleteMapping("/{slug}/invites/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void revokeInvite(@PathVariable String slug, @PathVariable long id, HttpSession session) {
    invitations.revoke(slug, id, session);
  }

  @PostMapping("/{slug}/invites/accept")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void acceptInvite(
      @PathVariable String slug,
      @Valid @RequestBody AcceptInvitation request,
      HttpSession session) {
    invitations.accept(slug, request.token(), session);
  }

  @GetMapping("/{slug}/templates")
  List<OrganizationTemplates.Template> templates(@PathVariable String slug, HttpSession session) {
    return templates.list(slug, session);
  }

  @PostMapping("/{slug}/templates")
  @ResponseStatus(HttpStatus.CREATED)
  OrganizationTemplates.Template createTemplate(
      @PathVariable String slug, @Valid @RequestBody Template request, HttpSession session) {
    return templates.create(slug, request.name(), request.kind(), request.description(), session);
  }

  @DeleteMapping("/{slug}/templates/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteTemplate(@PathVariable String slug, @PathVariable long id, HttpSession session) {
    templates.delete(slug, id, session);
  }

  @PostMapping("/{slug}/templates/{id}/projects")
  @ResponseStatus(HttpStatus.CREATED)
  Project instantiateTemplate(
      @PathVariable String slug,
      @PathVariable long id,
      @RequestParam(defaultValue = "EN") @Pattern(regexp = "EN|SV") String language,
      @Valid @RequestBody ProjectRequest request,
      HttpSession session) {
    return templates.instantiate(slug, id, request, language, session);
  }
}

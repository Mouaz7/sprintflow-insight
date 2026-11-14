package com.sprintflowinsight.project;

import com.sprintflowinsight.auth.SessionUser;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectAccessService {
  private final ProjectRepository projects;
  private final ProjectMemberRepository members;
  private final SessionUser sessionUser;
  private static final org.slf4j.Logger SECURITY_LOG =
      org.slf4j.LoggerFactory.getLogger("security.events");

  public ProjectAccessService(
      ProjectRepository projects, ProjectMemberRepository members, SessionUser sessionUser) {
    this.projects = projects;
    this.members = members;
    this.sessionUser = sessionUser;
  }

  public String email(HttpSession session) {
    return sessionUser.email(session);
  }

  public String role(Long projectId, HttpSession session) {
    return roleFor(projectId, email(session));
  }

  public String roleFor(Long projectId, String email) {
    Project project =
        projects
            .findById(projectId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
    if (project.getTrashedAt() != null)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project is in the recycle bin.");
    if (project.getOwnerEmail().equalsIgnoreCase(email)) return "Owner";
    return members
        .findByProjectIdAndEmailIgnoreCase(projectId, email)
        .map(ProjectMember::getRole)
        .orElseThrow(
            () -> {
              SECURITY_LOG.warn("Denied access to project {}.", projectId);
              return new ResponseStatusException(
                  HttpStatus.FORBIDDEN, "You do not have access to this project.");
            });
  }

  public void read(Long projectId, HttpSession session) {
    role(projectId, session);
  }

  public void write(Long projectId, HttpSession session) {
    String memberRole = role(projectId, session);
    if ("Viewer".equalsIgnoreCase(memberRole))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Viewers cannot change this project.");
    if (projects.findById(projectId).map(p -> p.getArchivedAt() != null).orElse(false))
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Unarchive this project before making changes.");
  }

  public void owner(Long projectId, HttpSession session) {
    if (!"Owner".equalsIgnoreCase(role(projectId, session)))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only an owner can manage team members.");
  }

  public void adminOrOwner(Long projectId, HttpSession session) {
    String role = role(projectId, session);
    if (!"Owner".equalsIgnoreCase(role) && !"Admin".equalsIgnoreCase(role))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Only an owner or admin can manage project settings and members.");
  }
}

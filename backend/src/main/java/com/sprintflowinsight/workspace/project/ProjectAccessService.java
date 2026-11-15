package com.sprintflowinsight.workspace.project;

import com.sprintflowinsight.auth.SessionUser;
import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.common.error.ErrorCode;
import com.sprintflowinsight.workspace.project.AccessContext.Need;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Central project authorisation. One query loads the project and the caller's membership ({@link
 * AccessContext}); the result is memoised for the rest of the HTTP request, so a handler that
 * checks several needs still costs a single round trip.
 */
@Service
public class ProjectAccessService {
  private static final Logger SECURITY_LOG = LoggerFactory.getLogger("security.events");
  private static final String CACHE_ATTRIBUTE = ProjectAccessService.class.getName() + ".cache";

  private final ProjectRepository projects;
  private final SessionUser sessionUser;

  public ProjectAccessService(ProjectRepository projects, SessionUser sessionUser) {
    this.projects = projects;
    this.sessionUser = sessionUser;
  }

  public String email(HttpSession session) {
    return sessionUser.email(session);
  }

  /** Verifies {@code need} for the signed-in user and returns the loaded context. */
  public AccessContext require(Long projectId, HttpSession session, Need need) {
    return require(projectId, email(session), need);
  }

  public AccessContext require(Long projectId, String email, Need need) {
    AccessContext context = context(projectId, email);
    if (context == null) throw new ApiException(ErrorCode.NOT_FOUND, "Project not found.");
    if (context.trashed())
      throw new ApiException(ErrorCode.NOT_FOUND, "Project is in the recycle bin.");
    if (!context.hasAccess()) deny(projectId, "You do not have access to this project.");
    ProjectRole role = context.role();
    switch (need) {
      case READ -> {}
      case WRITE -> {
        if (!role.canWrite()) deny(projectId, "Viewers cannot change this project.");
        requireActive(context);
      }
      case MANAGE, MANAGE_ARCHIVED -> {
        if (!role.canManage())
          deny(projectId, "Only an owner or admin can manage project settings and members.");
        if (need == Need.MANAGE) requireActive(context);
      }
      case OWNER, OWNER_ARCHIVED -> {
        if (role != ProjectRole.OWNER) deny(projectId, "Only an owner can manage team members.");
        if (need == Need.OWNER) requireActive(context);
      }
    }
    return context;
  }

  public String role(Long projectId, HttpSession session) {
    return require(projectId, session, Need.READ).role().label();
  }

  public String roleFor(Long projectId, String email) {
    return require(projectId, email, Need.READ).role().label();
  }

  public void read(Long projectId, HttpSession session) {
    require(projectId, session, Need.READ);
  }

  public void write(Long projectId, HttpSession session) {
    require(projectId, session, Need.WRITE);
  }

  public void owner(Long projectId, HttpSession session) {
    require(projectId, session, Need.OWNER);
  }

  public void adminOrOwner(Long projectId, HttpSession session) {
    require(projectId, session, Need.MANAGE);
  }

  /** Drops memoised contexts, e.g. after changing roles or archive state within one request. */
  public void forget(Long projectId) {
    Map<String, AccessContext> cache = cache();
    if (cache != null) cache.keySet().removeIf(key -> key.startsWith(projectId + ":"));
  }

  private AccessContext context(Long projectId, String email) {
    String normalized = email.trim().toLowerCase(Locale.ROOT);
    Map<String, AccessContext> cache = cache();
    String key = projectId + ":" + normalized;
    if (cache != null && cache.containsKey(key)) return cache.get(key);
    AccessContext context =
        projects
            .findAccessRow(projectId, normalized)
            .map(
                row -> {
                  ProjectRole role =
                      row.ownerEmail().equalsIgnoreCase(normalized)
                          ? ProjectRole.OWNER
                          : row.memberRole() == null
                              ? null
                              : ProjectRole.fromLabel(row.memberRole());
                  return new AccessContext(
                      projectId,
                      normalized,
                      role,
                      row.ownerEmail(),
                      row.archivedAt(),
                      row.trashedAt());
                })
            .orElse(null);
    if (cache != null) cache.put(key, context);
    return context;
  }

  private static void requireActive(AccessContext context) {
    if (context.archived())
      throw new ApiException(ErrorCode.CONFLICT, "Unarchive this project before making changes.");
  }

  private static void deny(Long projectId, String message) {
    SECURITY_LOG.warn("Denied access to project {}.", projectId);
    throw new ApiException(ErrorCode.FORBIDDEN, message);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, AccessContext> cache() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (attributes == null) return null;
    Object cache = attributes.getAttribute(CACHE_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
    if (cache == null) {
      cache = new HashMap<String, AccessContext>();
      attributes.setAttribute(CACHE_ATTRIBUTE, cache, RequestAttributes.SCOPE_REQUEST);
    }
    return (Map<String, AccessContext>) cache;
  }
}

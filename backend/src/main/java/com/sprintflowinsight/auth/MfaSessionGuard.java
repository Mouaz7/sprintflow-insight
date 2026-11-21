package com.sprintflowinsight.auth;

import com.sprintflowinsight.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/** Older sessions must authenticate again after an account enables its second factor. */
final class MfaSessionGuard extends OncePerRequestFilter {
  private final MfaService mfa;
  private final SecurityContextRepository contexts;
  private final ObjectMapper json;

  MfaSessionGuard(MfaService mfa, SecurityContextRepository contexts, ObjectMapper json) {
    this.mfa = mfa;
    this.contexts = contexts;
    this.json = json;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = path(request);
    return !path.startsWith("/api/") && !path.equals("/ws");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof UserIdentity user
        && !user.mfaVerified()) {
      if (mfa.needsReauthentication(user)) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        contexts.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
      } else if (workspaceRequest(path(request)) && mfa.organizationRequires(user.userId())) {
        if ("GET".equals(request.getMethod()) && "/api/projects".equals(path(request))) {
          // The shell can still load the account and open the two-factor settings.
          response.setContentType("application/json");
          response.getWriter().write("[]");
        } else {
          SecurityConfiguration.writeProblem(
              json,
              request,
              response,
              ErrorCode.FORBIDDEN,
              "Set up two-factor authentication to open your workspace.");
        }
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private static String path(HttpServletRequest request) {
    String servlet = request.getServletPath();
    return servlet == null || servlet.isEmpty()
        ? request.getRequestURI().substring(request.getContextPath().length())
        : servlet;
  }

  private static boolean workspaceRequest(String path) {
    return !path.startsWith("/api/auth/")
        && !path.startsWith("/api/shared-reports/")
        && !path.equals("/api/account/export");
  }
}

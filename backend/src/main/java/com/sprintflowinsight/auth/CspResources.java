package com.sprintflowinsight.auth;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CspResources implements WebMvcConfigurer {
  /**
   * Client-side routes of the single-page app. A reload or shared link on one of these paths
   * serves the same nonce-transformed document as {@code /}; keep in sync with the frontend
   * router ({@code frontend/src/app/App.tsx} and {@code WorkspaceRoutes.tsx}).
   */
  static final List<String> SPA_ROUTES =
      List.of(
          "/overview",
          "/projects",
          "/backlog",
          "/sprints",
          "/tasks",
          "/metrics",
          "/updates",
          "/profile",
          "/calendar",
          "/retrospective",
          "/trash",
          "/reset-password",
          "/report/{token}");

  private final CspPolicy policy;

  public CspResources(CspPolicy policy) {
    this.policy = policy;
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/index.html")
        .addResourceLocations("classpath:/static/")
        .setUseLastModified(false)
        .setCacheControl(CacheControl.noStore())
        .resourceChain(false)
        .addTransformer(policy);
  }

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    for (String route : SPA_ROUTES)
      registry.addViewController(route).setViewName("forward:/index.html");
  }
}

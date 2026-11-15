package com.sprintflowinsight.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CspResources implements WebMvcConfigurer {
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
}

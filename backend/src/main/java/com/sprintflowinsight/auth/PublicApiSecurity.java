package com.sprintflowinsight.auth;

import com.sprintflowinsight.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
class PublicApiSecurity {
  private static final String PROJECTS = "/api/v1/projects";
  private static final String PROJECT = PROJECTS + "/{projectId}";
  private static final String TASKS = PROJECT + "/tasks";
  private static final String DOCS = "/api/v1/openapi";

  @Bean
  @Order(1)
  SecurityFilterChain publicApiChain(
      HttpSecurity http, PersonalAccessTokens tokens, ObjectMapper json) throws Exception {
    var paths = PathPatternRequestMatcher.withDefaults();
    return http.securityMatcher(paths.matcher("/api/v1/**"))
        .securityContext(
            context -> context.securityContextRepository(new NullSecurityContextRepository()))
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .csrf(csrf -> csrf.disable())
        .logout(logout -> logout.disable())
        .formLogin(login -> login.disable())
        .httpBasic(basic -> basic.disable())
        .addFilterAfter(new BearerFilter(tokens), SecurityContextHolderFilter.class)
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, failure) -> {
                          response.setHeader(
                              HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"SprintFlow API\"");
                          SecurityConfiguration.writeProblem(
                              json,
                              request,
                              response,
                              ErrorCode.UNAUTHENTICATED,
                              "Supply a valid SprintFlow access token.");
                        })
                    .accessDeniedHandler(
                        (request, response, failure) ->
                            SecurityConfiguration.writeProblem(
                                json,
                                request,
                                response,
                                ErrorCode.FORBIDDEN,
                                "The access token does not have the required scope.")))
        .authorizeHttpRequests(
            authorization ->
                authorization
                    .requestMatchers(HttpMethod.GET, DOCS, DOCS + ".yaml")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, PROJECTS, PROJECT)
                    .hasAuthority("SCOPE_PROJECT_READ")
                    .requestMatchers(HttpMethod.GET, TASKS, TASKS + "/{taskId}")
                    .hasAuthority("SCOPE_TASK_READ")
                    .requestMatchers(HttpMethod.POST, TASKS)
                    .hasAuthority("SCOPE_TASK_WRITE")
                    .anyRequest()
                    .denyAll())
        .build();
  }

  private static final class BearerFilter extends OncePerRequestFilter {
    private static final String SCHEME = "Bearer ";
    private final PersonalAccessTokens tokens;

    private BearerFilter(PersonalAccessTokens tokens) {
      this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      var headers = request.getHeaders(HttpHeaders.AUTHORIZATION);
      if (headers.hasMoreElements()) {
        String header = headers.nextElement();
        if (!headers.hasMoreElements()
            && header.regionMatches(true, 0, SCHEME, 0, SCHEME.length())) {
          tokens
              .authenticate(header.substring(SCHEME.length()))
              .ifPresent(
                  authentication -> {
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(authentication);
                    SecurityContextHolder.setContext(context);
                  });
        }
      }
      chain.doFilter(request, response);
    }
  }
}

package com.sprintflowinsight;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.security.auth-rate-limit.enabled=false",
      "spring.datasource.url=jdbc:h2:mem:security-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false",
      "spring.session.jdbc.initialize-schema=always"
    })
@ActiveProfiles("dev")
@org.junit.jupiter.api.extension.ExtendWith(
    org.springframework.boot.test.system.OutputCaptureExtension.class)
class SecurityConfigurationTest {
  @Autowired TestRestTemplate rest;
  @LocalServerPort int port;

  @Test
  void protectsApiAndSendsSecurityHeaders() {
    ResponseEntity<String> protectedResponse =
        rest.getForEntity(url("/api/projects"), String.class);
    assertThat(protectedResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    ResponseEntity<String> publicResponse = rest.getForEntity(url("/api/auth/csrf"), String.class);
    assertThat(publicResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(publicResponse.getHeaders().getFirst("Content-Security-Policy"))
        .contains("default-src 'self'");
    assertThat(publicResponse.getHeaders().getFirst("Referrer-Policy"))
        .isEqualTo("strict-origin-when-cross-origin");
    assertThat(publicResponse.getHeaders().getFirst("Permissions-Policy"))
        .contains("camera=()", "payment=()");
    assertThat(publicResponse.getHeaders().getFirst("Content-Security-Policy"))
        .contains("frame-ancestors 'none'", "object-src 'none'");
    assertThat(publicResponse.getHeaders().getFirst("Cross-Origin-Resource-Policy"))
        .isEqualTo("same-origin");
    assertThat(publicResponse.getHeaders().getFirst("Content-Security-Policy-Report-Only"))
        .isEqualTo("require-trusted-types-for 'script'");
  }

  @Test
  void localLoginSupportsGooglePopupsWithoutExposingQueryTokens() {
    ResponseEntity<String> login = rest.getForEntity(url("/"), String.class);
    assertThat(login.getHeaders().getFirst("Referrer-Policy"))
        .isEqualTo("no-referrer-when-downgrade");
    assertThat(login.getHeaders().getFirst("Cross-Origin-Opener-Policy"))
        .isEqualTo("same-origin-allow-popups");
    for (String query :
        java.util.List.of("?resetToken=private-reset-token", "?report=private-share-token")) {
      ResponseEntity<String> response = rest.getForEntity(url("/" + query), String.class);
      assertThat(response.getHeaders().getFirst("Referrer-Policy"))
          .isEqualTo("strict-origin-when-cross-origin");
    }
  }

  @Test
  void rejectsStateChangingRequestWithoutCsrfToken(
      org.springframework.boot.test.system.CapturedOutput output) {
    ResponseEntity<String> response =
        rest.exchange(
            url("/api/projects"),
            HttpMethod.POST,
            new org.springframework.http.HttpEntity<>(
                "{\"name\":\"No"
                    + " token\",\"password\":\"private-password-not-for-logs\",\"token\":\"private-token-not-for-logs\"}"),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(output.getOut())
        .contains("Denied protected request.")
        .doesNotContain("private-password-not-for-logs", "private-token-not-for-logs");
  }

  @Test
  void forbiddenApiResponseIsLoggedWithoutSensitiveReason(
      org.springframework.boot.test.system.CapturedOutput output) throws Exception {
    String sensitiveReason = "private-api-error-token-not-for-logs";
    ResponseEntity<?> response =
        new ApiExceptionHandler()
            .handleException(
                new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, sensitiveReason),
                new org.springframework.web.context.request.ServletWebRequest(
                    new org.springframework.mock.web.MockHttpServletRequest()));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(output.getOut()).contains("Denied API operation.").doesNotContain(sensitiveReason);
  }

  @Test
  void healthIsPublicAndDoesNotExposeComponentsOrConfiguration() {
    ResponseEntity<String> response = rest.getForEntity(url("/actuator/health"), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo("{\"status\":\"UP\"}");
    assertThat(rest.getForEntity(url("/actuator/env"), String.class).getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
  }

  private String url(String path) {
    return "http://localhost:" + port + path;
  }

  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.context.ApplicationContext applicationContext;

  @org.junit.jupiter.api.Test
  void reviewedSpringViewAdvisoriesRemainInapplicable() {
    org.assertj.core.api.Assertions.assertThat(
            applicationContext.getBeansOfType(
                org.springframework.web.servlet.view.xslt.XsltViewResolver.class))
        .isEmpty();
    applicationContext
        .getBeansOfType(
            org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
                .class)
        .values()
        .forEach(
            mapping ->
                mapping
                    .getHandlerMethods()
                    .values()
                    .forEach(
                        handler -> {
                          Class<?> type = handler.getReturnType().getParameterType();
                          org.assertj.core.api.Assertions.assertThat(
                                  org.springframework.web.servlet.mvc.method.annotation
                                      .ResponseBodyEmitter.class
                                      .isAssignableFrom(type))
                              .isFalse();
                          org.assertj.core.api.Assertions.assertThat(
                                  org.springframework.web.servlet.view.FragmentsRendering.class
                                      .isAssignableFrom(type))
                              .isFalse();
                        }));
  }
}

package com.sprintflowinsight;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@org.springframework.context.annotation.Import(
    com.sprintflowinsight.support.PostgresTestContainer.class)
@org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.security.auth-rate-limit.enabled=false"})
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
    assertThat(publicResponse.getHeaders().getFirst("Content-Security-Policy"))
        .contains(
            "require-trusted-types-for 'script'",
            "trusted-types sprintflow-google sprintflow-pwa goog#html 'allow-duplicates'",
            "script-src 'none'");
    assertThat(publicResponse.getHeaders().getFirst("Content-Security-Policy-Report-Only"))
        .isNull();
  }

  @Test
  void builtFrontendGetsAFreshMatchingNonceAndCannotReturnAStale304() {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        new org.springframework.core.io.ClassPathResource("static/index.html").exists(),
        "Build frontend first to exercise its packaged document.");
    var first = rest.getForEntity(url("/"), String.class);
    var second = rest.getForEntity(url("/index.html"), String.class);
    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(first.getHeaders().getCacheControl()).contains("no-store");
    var pattern = java.util.regex.Pattern.compile("'nonce-([A-Za-z0-9_-]{32})'");
    var firstNonce = pattern.matcher(first.getHeaders().getFirst("Content-Security-Policy"));
    var secondNonce = pattern.matcher(second.getHeaders().getFirst("Content-Security-Policy"));
    assertThat(firstNonce.find()).isTrue();
    assertThat(secondNonce.find()).isTrue();
    assertThat(firstNonce.group(1)).isNotEqualTo(secondNonce.group(1));
    assertThat(first.getBody())
        .contains("nonce=\"" + firstNonce.group(1) + "\"")
        .doesNotContain("__SPRINTFLOW_CSP_NONCE__");
    var headers = new org.springframework.http.HttpHeaders();
    headers.setIfModifiedSince(System.currentTimeMillis() + 86400000);
    var conditional =
        rest.exchange(
            url("/index.html"),
            HttpMethod.GET,
            new org.springframework.http.HttpEntity<>(headers),
            String.class);
    assertThat(conditional.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void clientRoutesServeTheNonceDocumentAndKeepLinkTokensOutOfReferrers() {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        new org.springframework.core.io.ClassPathResource("static/index.html").exists(),
        "Build frontend first to exercise its packaged document.");
    for (String path :
        java.util.List.of(
            "/tasks",
            "/poker?session=1",
            "/time?sprint=1",
            "/dashboard",
            "/email-preferences?token=preview",
            "/roadmap?epic=1",
            "/report/private-share-token",
            "/reset-password?token=private-reset")) {
      ResponseEntity<String> response = rest.getForEntity(url(path), String.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      var nonce =
          java.util.regex.Pattern.compile("'nonce-([A-Za-z0-9_-]{32})'")
              .matcher(response.getHeaders().getFirst("Content-Security-Policy"));
      assertThat(nonce.find()).isTrue();
      assertThat(response.getBody()).contains("nonce=\"" + nonce.group(1) + "\"");
      assertThat(response.getHeaders().getCacheControl()).contains("no-store");
      assertThat(response.getHeaders().getFirst("Referrer-Policy"))
          .isEqualTo("strict-origin-when-cross-origin");
    }
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

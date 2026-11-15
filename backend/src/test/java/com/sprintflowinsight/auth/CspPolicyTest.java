package com.sprintflowinsight.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CspPolicyTest {
  private final CspPolicy policy = new CspPolicy();
  private final org.springframework.web.servlet.resource.ResourceTransformerChain chain =
      new org.springframework.web.servlet.resource.ResourceTransformerChain() {
        @Override
        public org.springframework.web.servlet.resource.ResourceResolverChain getResolverChain() {
          return null;
        }

        @Override
        public Resource transform(
            jakarta.servlet.http.HttpServletRequest request, Resource current) {
          return current;
        }
      };

  private Resource resource(String name, String text) {
    return new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8)) {
      @Override
      public String getFilename() {
        return name;
      }

      @Override
      public long lastModified() {
        return 0; // classpath resources in the packaged JAR report a timestamp
      }
    };
  }

  private String document(MockHttpServletRequest request) throws Exception {
    var source =
        resource(
            "index.html",
            "<meta nonce=\""
                + CspPolicy.PLACEHOLDER
                + "\"><script nonce=\""
                + CspPolicy.PLACEHOLDER
                + "\"></script>");
    var transformed = policy.transform(request, source, chain);
    return new String(transformed.getContentAsByteArray(), StandardCharsets.UTF_8);
  }

  @Test
  void rendersUnpredictableFreshNoncesAndUsesTheSameValueInTheHeader() throws Exception {
    var request = new MockHttpServletRequest();
    String first = document(request);
    var response = new MockHttpServletResponse();
    policy.writeHeaders(request, response);
    var matcher = java.util.regex.Pattern.compile("nonce=\"([A-Za-z0-9_-]{32})\"").matcher(first);
    assertThat(matcher.find()).isTrue();
    String nonce = matcher.group(1);
    assertThat(first).doesNotContain(CspPolicy.PLACEHOLDER);
    assertThat(document(request)).isEqualTo(first);
    assertThat(document(new MockHttpServletRequest())).isNotEqualTo(first);
    assertThat(response.getHeader("Content-Security-Policy"))
        .contains(
            "'nonce-" + nonce + "'",
            "'strict-dynamic'",
            "require-trusted-types-for 'script'",
            "trusted-types sprintflow-google goog#html 'allow-duplicates'")
        .doesNotContain("'unsafe-eval'", "trusted-types default", "trusted-types *", "trusted-types 'none'");
  }

  @Test
  void leavesApiAndImmutableAssetsUnchangedAndAuthorizesNoScriptsForThem() throws Exception {
    var request = new MockHttpServletRequest();
    var script = resource("app-hashed.js", "const marker = '" + CspPolicy.PLACEHOLDER + "';");
    assertThat(policy.transform(request, script, chain)).isSameAs(script);
    var response = new MockHttpServletResponse();
    response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
    policy.writeHeaders(request, response);
    assertThat(response.getHeader("Cache-Control"))
        .isEqualTo("public, max-age=31536000, immutable");
    assertThat(response.getHeader("Content-Security-Policy"))
        .contains("script-src 'none'")
        .doesNotContain("'nonce-");
  }
}

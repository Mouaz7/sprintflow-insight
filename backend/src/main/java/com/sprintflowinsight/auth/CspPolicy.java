package com.sprintflowinsight.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.core.io.Resource;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.resource.ResourceTransformer;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

/** Couples each rendered SPA document to its nonce without rewriting API responses or assets. */
@Component
public final class CspPolicy implements HeaderWriter, ResourceTransformer {
  public static final String PLACEHOLDER = "__SPRINTFLOW_CSP_NONCE__";
  private static final String ATTRIBUTE = CspPolicy.class.getName() + ".nonce";
  private final SecureRandom random = new SecureRandom();

  @Override
  public Resource transform(
      HttpServletRequest request, Resource resource, ResourceTransformerChain chain)
      throws IOException {
    Resource transformed = chain.transform(request, resource);
    if (!"index.html".equals(transformed.getFilename())) return transformed;
    String nonce = nonce(request);
    String html;
    try (var stream = transformed.getInputStream()) {
      html = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
    return new TransformedResource(
        transformed, html.replace(PLACEHOLDER, nonce).getBytes(StandardCharsets.UTF_8));
  }

  private String nonce(HttpServletRequest request) {
    String existing = (String) request.getAttribute(ATTRIBUTE);
    if (existing != null) return existing;
    byte[] bytes = new byte[24];
    random.nextBytes(bytes);
    String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    request.setAttribute(ATTRIBUTE, nonce);
    return nonce;
  }

  @Override
  public void writeHeaders(HttpServletRequest request, HttpServletResponse response) {
    String nonce = (String) request.getAttribute(ATTRIBUTE);
    // Only documents transformed by the index handler authorize scripts. API/asset bodies
    // stay byte-for-byte unchanged, and immutable assets keep their normal cache behaviour.
    String scripts =
        nonce == null
            ? "'none'"
            : "'nonce-"
                + nonce
                + "' 'strict-dynamic' 'self' https://accounts.google.com/gsi/client";
    String styleNonce = nonce == null ? "" : " 'nonce-" + nonce + "'";
    response.setHeader(
        "Content-Security-Policy",
        "default-src 'self'; script-src "
            + scripts
            + "; style-src 'self'"
            + styleNonce
            + " https://fonts.googleapis.com https://accounts.google.com/gsi/style;"
            // React assigns typed CSS properties and GIS renders width styles. This exception
            // permits style attributes, never inline scripts, style elements or eval.
            + " style-src-attr 'unsafe-inline'; font-src 'self' https://fonts.gstatic.com;"
            + " img-src 'self' data: https:; connect-src 'self' https://accounts.google.com/gsi/;"
            + " frame-src https://accounts.google.com/gsi/; object-src 'none'; base-uri 'none';"
            + " form-action 'self'; frame-ancestors 'none'; require-trusted-types-for 'script';"
            // Google Identity Services creates its Closure goog#html policy itself and may do so
            // again (for example when its loader runs twice). Without 'allow-duplicates' that
            // second createPolicy throws and breaks sign-in. Only the two named policies remain
            // allowed; enforcement and the sink checks are unchanged.
            + " trusted-types sprintflow-google goog#html 'allow-duplicates'");
  }
}

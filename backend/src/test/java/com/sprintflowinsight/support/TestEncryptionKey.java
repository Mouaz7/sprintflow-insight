package com.sprintflowinsight.support;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Tests only: when no encryption key is configured, supply one random key per test JVM. All Spring
 * contexts share the test database, so they must share this key too. Production code has no such
 * fallback and refuses to start without {@code SPRINTFLOW_ENCRYPTION_KEY}.
 */
public class TestEncryptionKey implements EnvironmentPostProcessor {
  public static final String KEY = generate();

  private static String generate() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    String configured = environment.getProperty("app.encryption.key");
    if (configured == null || configured.isBlank())
      environment
          .getPropertySources()
          .addFirst(new MapPropertySource("testEncryptionKey", Map.of("app.encryption.key", KEY)));
  }
}

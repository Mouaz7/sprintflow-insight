package com.sprintflowinsight.realtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.realtime.relay")
record RelaySettings(
    boolean enabled, String host, int port, String login, String passcode, String virtualHost) {
  RelaySettings {
    if (enabled
        && (blank(host)
            || blank(login)
            || blank(passcode)
            || blank(virtualHost)
            || port < 1
            || port > 65535)) {
      throw new IllegalArgumentException(
          "Broker relay requires a host, valid port, credentials and virtual host.");
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  @Override
  public String toString() {
    return "RelaySettings[enabled=" + enabled + ", credentials=redacted]";
  }
}

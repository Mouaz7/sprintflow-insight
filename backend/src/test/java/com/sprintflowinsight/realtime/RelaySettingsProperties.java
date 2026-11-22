package com.sprintflowinsight.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class RelaySettingsProperties {
  @Property(seed = "2026112106", tries = 100)
  void acceptsValidPortsWithoutIncludingCredentialsInDiagnostics(
      @ForAll @IntRange(min = 1, max = 65535) int port) {
    var settings = settings(port);
    assertThat(settings.port()).isEqualTo(port);
    assertThat(settings.toString()).doesNotContain("relay-user", "relay-credential");
  }

  @Property(seed = "2026112106", tries = 100)
  void rejectsPortsOutsideTheTcpRange(@ForAll @IntRange(min = 0, max = 10_000) int offset) {
    assertThatIllegalArgumentException().isThrownBy(() -> settings(-offset));
    assertThatIllegalArgumentException().isThrownBy(() -> settings(65536 + offset));
  }

  @Test
  void validatesBothPortBoundariesAndAllRequiredValues() {
    assertThat(settings(1).enabled()).isTrue();
    assertThat(settings(65535).enabled()).isTrue();
    assertThatIllegalArgumentException().isThrownBy(() -> settings(0));
    assertThatIllegalArgumentException().isThrownBy(() -> settings(65536));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RelaySettings(true, null, 61613, "a", "b", "/"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RelaySettings(true, " ", 61613, "a", "b", "/"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RelaySettings(true, "rabbitmq", 61613, "", "b", "/"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RelaySettings(true, "rabbitmq", 61613, "a", "", "/"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RelaySettings(true, "rabbitmq", 61613, "a", "b", ""));
    assertThat(new RelaySettings(false, null, 0, null, null, null).enabled()).isFalse();
  }

  private static RelaySettings settings(int port) {
    return new RelaySettings(true, "rabbitmq", port, "relay-user", "relay-credential", "/");
  }
}

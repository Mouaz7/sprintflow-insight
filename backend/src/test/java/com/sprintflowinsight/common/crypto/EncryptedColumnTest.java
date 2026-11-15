package com.sprintflowinsight.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EncryptedColumnTest {
  @Test
  void conventionalContextIsTableDotColumn() {
    assertThat(EncryptedColumn.of("webhook", "id", "secret").context()).isEqualTo("webhook.secret");
  }

  @Test
  void rejectsAnythingThatIsNotAPlainIdentifier() {
    for (String bad : new String[] {null, "", "Webhook", "web hook", "x;drop table users", "1abc"}) {
      assertThatThrownBy(() -> EncryptedColumn.of(bad, "id", "secret"))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> EncryptedColumn.of("webhook", bad, "secret"))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> EncryptedColumn.of("webhook", "id", bad))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> new EncryptedColumn("webhook", "id", "secret", " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EncryptedColumn("webhook", "id", "secret", null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

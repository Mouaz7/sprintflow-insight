package com.sprintflowinsight.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class AuditChainHashTest {
  @Property(tries = 200, seed = "2026112107")
  void detectsChangedContentLinksAndPosition(
      @ForAll long seed, @ForAll @IntRange(min = 1, max = 30) int length) {
    var random = new Random(seed);
    byte[] previous = new byte[32];
    for (long position = 1; position <= length; position++) {
      byte[] payload = new byte[random.nextInt(1, 512)];
      random.nextBytes(payload);
      byte[] hash = AuditChainHash.link(previous, payload);
      byte[] damaged = payload.clone();
      damaged[random.nextInt(damaged.length)] ^= 1;
      assertThat(AuditChainHash.links(position, previous, position, previous, payload, hash))
          .isTrue();
      assertThat(AuditChainHash.links(position, previous, position + 1, previous, payload, hash))
          .isFalse();
      assertThat(AuditChainHash.links(position, previous, position, previous, damaged, hash))
          .isFalse();
      byte[] foreign = previous.clone();
      foreign[0] ^= 1;
      assertThat(AuditChainHash.links(position, previous, position, foreign, payload, hash))
          .isFalse();
      byte[] incorrect = hash.clone();
      incorrect[4] ^= 2;
      assertThat(AuditChainHash.links(position, previous, position, previous, payload, incorrect))
          .isFalse();
      previous = hash;
    }
  }

  @Test
  void enforcesTheFixedPredecessorSizeAndMatchesTheSha256Reference() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> AuditChainHash.link(new byte[31], new byte[0]));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> AuditChainHash.link(new byte[33], new byte[0]));
    assertThat(java.util.HexFormat.of().formatHex(AuditChainHash.link(new byte[32], new byte[0])))
        .isEqualTo("66687aadf862bd776c8fc18b8e9f8e20089714856ee233b3902a591d0d5f2925");
  }
}

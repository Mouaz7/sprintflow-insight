package com.sprintflowinsight.crdt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class CrdtFramesTest {
  @Property(tries = 100, seed = "2026112105")
  void preservesOpaqueUpdatesAndTheirOrder(
      @ForAll long seed, @ForAll @IntRange(min = 0, max = 64) int count) {
    var random = new Random(seed);
    List<byte[]> updates =
        IntStream.range(0, count)
            .mapToObj(
                index -> {
                  byte[] frame = new byte[random.nextInt(1, 301)];
                  random.nextBytes(frame);
                  return frame;
                })
            .toList();
    List<byte[]> restored = CrdtFrames.unpack(CrdtFrames.pack(updates));
    assertThat(restored).hasSize(updates.size());
    for (int i = 0; i < updates.size(); i++)
      assertThat(restored.get(i)).containsExactly(updates.get(i));
  }

  @Test
  void rejectsCorruptAndOversizedFrames() {
    assertThatIllegalArgumentException().isThrownBy(() -> CrdtFrames.pack(List.of(new byte[0])));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> CrdtFrames.pack(List.of(new byte[CrdtFrames.MAX_UPDATE_BYTES + 1])));
    int tooManyBytes = CrdtFrames.MAX_DOCUMENT_BYTES / CrdtFrames.MAX_UPDATE_BYTES + 1;
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                CrdtFrames.pack(
                    Collections.nCopies(tooManyBytes, new byte[CrdtFrames.MAX_UPDATE_BYTES])));
    byte[] valid = CrdtFrames.pack(List.of(new byte[] {1, 2, 3, 4}));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> CrdtFrames.unpack(Arrays.copyOf(valid, valid.length - 2)));
    assertThatIllegalArgumentException().isThrownBy(() -> CrdtFrames.unpack(new byte[0]));
  }
}

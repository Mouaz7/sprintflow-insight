package com.sprintflowinsight.crdt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.zip.GZIPOutputStream;
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

  @Test
  void acceptsTheExactFrameCountAndFramedByteLimits() {
    var frames = Collections.nCopies(CrdtFrames.MAX_FRAME_COUNT, new byte[] {7});
    assertThat(CrdtFrames.unpack(CrdtFrames.pack(frames))).hasSize(CrdtFrames.MAX_FRAME_COUNT);
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                CrdtFrames.pack(
                    Collections.nCopies(CrdtFrames.MAX_FRAME_COUNT + 1, new byte[] {7})));

    int budget = CrdtFrames.MAX_DOCUMENT_BYTES - Integer.BYTES;
    int fullLength = CrdtFrames.MAX_UPDATE_BYTES + Integer.BYTES;
    var atLimit =
        new ArrayList<>(
            Collections.nCopies(budget / fullLength, new byte[CrdtFrames.MAX_UPDATE_BYTES]));
    atLimit.add(new byte[budget % fullLength - Integer.BYTES]);
    assertThat(CrdtFrames.unpack(CrdtFrames.pack(atLimit))).hasSize(atLimit.size());
  }

  @Test
  void rejectsMalformedHeadersTrailingBytesAndOversizedCompressedInput() throws IOException {
    byte[] negativeCount = compressed(-1);
    byte[] oversizedCount = compressed(CrdtFrames.MAX_FRAME_COUNT + 1);
    byte[] trailing = compressed(0, 42);
    for (byte[] invalid :
        List.of(
            negativeCount,
            oversizedCount,
            trailing,
            new byte[CrdtFrames.MAX_DOCUMENT_BYTES + 4097])) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> CrdtFrames.unpack(invalid))
          .withMessageContaining("frame bounds");
    }
  }

  private static byte[] compressed(int... words) throws IOException {
    var buffer = new ByteArrayOutputStream();
    try (var out = new DataOutputStream(new GZIPOutputStream(buffer))) {
      for (int word : words) out.writeInt(word);
    }
    return buffer.toByteArray();
  }
}

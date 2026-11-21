package com.sprintflowinsight.crdt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Server-side snapshots preserve each opaque Yjs update in a compressed, bounded frame. */
public final class CrdtFrames {
  public static final int MAX_UPDATE_BYTES = 12 * 1024;
  public static final int MAX_FRAME_COUNT = 16_384;
  public static final int MAX_DOCUMENT_BYTES = 2 * 1024 * 1024;

  private CrdtFrames() {}

  public static byte[] pack(List<byte[]> updates) {
    if (updates.size() > MAX_FRAME_COUNT) throw invalid();
    var buffer = new ByteArrayOutputStream();
    try (var out = new DataOutputStream(new GZIPOutputStream(buffer))) {
      out.writeInt(updates.size());
      int size = Integer.BYTES;
      for (byte[] update : updates) {
        size = checkSize(size, update.length);
        out.writeInt(update.length);
        out.write(update);
      }
    } catch (IOException error) {
      throw new UncheckedIOException("Could not compress collaboration snapshot.", error);
    }
    return buffer.toByteArray();
  }

  public static List<byte[]> unpack(byte[] snapshot) {
    if (snapshot.length == 0 || snapshot.length > MAX_DOCUMENT_BYTES + 4096) throw invalid();
    try (var in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(snapshot)))) {
      int count = in.readInt();
      if (count < 0 || count > MAX_FRAME_COUNT) throw invalid();
      var updates = new ArrayList<byte[]>(count);
      int size = Integer.BYTES;
      for (int i = 0; i < count; i++) {
        int length = in.readInt();
        size = checkSize(size, length);
        byte[] update = new byte[length];
        in.readFully(update);
        updates.add(update);
      }
      if (in.read() != -1) throw invalid();
      return List.copyOf(updates);
    } catch (IOException error) {
      throw new IllegalArgumentException("Malformed collaboration snapshot.", error);
    }
  }

  private static int checkSize(int previous, int length) {
    if (length < 1 || length > MAX_UPDATE_BYTES) throw invalid();
    int size = previous + Integer.BYTES + length;
    if (size > MAX_DOCUMENT_BYTES) throw invalid();
    return size;
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("The collaboration snapshot exceeds the frame bounds.");
  }
}

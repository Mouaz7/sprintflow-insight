package com.sprintflowinsight.common.audit;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** The link format shared with PostgreSQL: SHA-256 of a 32-byte predecessor and the event bytes. */
final class AuditChainHash {
  static final int HASH_BYTES = 32;

  private AuditChainHash() {}

  static byte[] link(byte[] predecessor, byte[] event) {
    if (predecessor.length != HASH_BYTES) throw new IllegalArgumentException("Invalid audit hash.");
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      digest.update(predecessor);
      return digest.digest(event);
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("SHA-256 is unavailable.", error);
    }
  }

  static boolean links(
      long expectedPosition,
      byte[] expectedPredecessor,
      long position,
      byte[] predecessor,
      byte[] payload,
      byte[] result) {
    return expectedPosition == position
        && MessageDigest.isEqual(expectedPredecessor, predecessor)
        && MessageDigest.isEqual(link(expectedPredecessor, payload), result);
  }
}

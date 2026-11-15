package com.sprintflowinsight.common.crypto;

/**
 * A value could not be encrypted or decrypted: unknown key, malformed envelope, or a ciphertext,
 * key id or context that was changed after encryption. Never carries key or plaintext material.
 */
public class EncryptionException extends RuntimeException {
  public EncryptionException(String message) {
    super(message);
  }

  public EncryptionException(String message, Throwable cause) {
    super(message, cause);
  }
}

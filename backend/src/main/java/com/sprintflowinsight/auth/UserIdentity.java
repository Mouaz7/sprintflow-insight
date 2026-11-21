package com.sprintflowinsight.auth;

import java.io.Serializable;
import java.security.Principal;

/** Session ownership uses the immutable account ID, even after an email change. */
public record UserIdentity(
    Long userId, String email, boolean mfaVerified, long authenticatedAtMillis)
    implements Principal, Serializable {
  public UserIdentity(Long userId, String email) {
    this(userId, email, false, 0);
  }

  @Override
  public String getName() {
    return email;
  }
}

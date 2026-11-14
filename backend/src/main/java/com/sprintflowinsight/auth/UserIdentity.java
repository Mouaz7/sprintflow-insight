package com.sprintflowinsight.auth;

import java.io.Serializable;
import java.security.Principal;

/** Session ownership uses the immutable account ID, even after an email change. */
public record UserIdentity(Long userId, String email) implements Principal, Serializable {
  @Override
  public String getName() {
    return email;
  }
}

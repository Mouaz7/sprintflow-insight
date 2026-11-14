package com.sprintflowinsight;

import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A mutation must name the revision the caller actually read. */
public final class VersionPrecondition {
  private VersionPrecondition() {}

  public static void require(String ifMatch, Long version) {
    if (ifMatch == null || ifMatch.isBlank())
      throw new ResponseStatusException(
          HttpStatus.PRECONDITION_REQUIRED,
          "Reload this item before changing it; If-Match is required.");
    if (!Objects.equals(ifMatch, "\"" + version + "\""))
      throw new ResponseStatusException(
          HttpStatus.PRECONDITION_FAILED,
          "This item was changed by someone else. Reload it and review your changes.");
  }
}

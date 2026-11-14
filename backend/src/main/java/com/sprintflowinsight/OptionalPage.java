package com.sprintflowinsight;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Optional pagination preserves existing clients that expect complete lists. */
public final class OptionalPage {
  private OptionalPage() {}

  public static Pageable of(Integer page, int size) {
    if (size < 1 || size > 200 || (page != null && page < 0)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Page must be zero or greater and size must be between 1 and 200.");
    }
    return page == null ? Pageable.unpaged() : PageRequest.of(page, size);
  }
}

package com.sprintflowinsight.search;

import java.util.Locale;

/**
 * Searchable item kinds. {@link #order} breaks rank ties (higher first) and is part of the keyset
 * cursor, so existing values must not change.
 */
public enum SearchType {
  TASK(5),
  STORY(4),
  SPRINT(3),
  COMMENT(2),
  PROJECT(1);

  /** Accepted values of the {@code types} parameter. */
  public static final String WIRE_PATTERN = "task|story|sprint|comment|project";

  private final int order;

  SearchType(int order) {
    this.order = order;
  }

  public int order() {
    return order;
  }

  /** The lower-case name used in requests and responses. */
  public String wire() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static SearchType fromWire(String value) {
    return valueOf(value.trim().toUpperCase(Locale.ROOT));
  }

  public static SearchType fromOrder(int order) {
    for (SearchType type : values()) if (type.order == order) return type;
    throw new IllegalArgumentException("Unknown search type order " + order);
  }
}

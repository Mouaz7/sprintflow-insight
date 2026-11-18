package com.sprintflowinsight.reporting;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Query parameters of the flow endpoint: the window length in whole UTC days, ending today. */
public record FlowQuery(@Min(MIN_DAYS) @Max(MAX_DAYS) Integer days) {
  public static final int MIN_DAYS = 7;
  public static final int MAX_DAYS = 180;
  public static final int DEFAULT_DAYS = 30;

  public int windowDays() {
    return days == null ? DEFAULT_DAYS : days;
  }
}

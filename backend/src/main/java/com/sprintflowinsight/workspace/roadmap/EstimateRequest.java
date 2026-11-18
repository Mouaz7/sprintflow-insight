package com.sprintflowinsight.workspace.roadmap;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** A three-point estimate in working days. */
public record EstimateRequest(
    @NotNull(message = "The optimistic estimate is required.")
        @DecimalMin(value = "0", message = "Estimates cannot be negative.")
        @DecimalMax(value = "1000", message = "Estimates can be at most 1000 days.")
        @Digits(integer = 4, fraction = 2, message = "Use at most two decimals.")
        BigDecimal optimistic,
    @NotNull(message = "The most likely estimate is required.")
        @DecimalMin(value = "0", message = "Estimates cannot be negative.")
        @DecimalMax(value = "1000", message = "Estimates can be at most 1000 days.")
        @Digits(integer = 4, fraction = 2, message = "Use at most two decimals.")
        BigDecimal mostLikely,
    @NotNull(message = "The pessimistic estimate is required.")
        @DecimalMin(value = "0", message = "Estimates cannot be negative.")
        @DecimalMax(value = "1000", message = "Estimates can be at most 1000 days.")
        @Digits(integer = 4, fraction = 2, message = "Use at most two decimals.")
        BigDecimal pessimistic) {

  @AssertTrue(message = "Estimates must be ordered: optimistic ≤ most likely ≤ pessimistic.")
  public boolean isOrdered() {
    return optimistic == null
        || mostLikely == null
        || pessimistic == null
        || (optimistic.compareTo(mostLikely) <= 0 && mostLikely.compareTo(pessimistic) <= 0);
  }
}

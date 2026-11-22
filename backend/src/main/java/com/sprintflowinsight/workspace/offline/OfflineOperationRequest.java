package com.sprintflowinsight.workspace.offline;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record OfflineOperationRequest(
    @NotNull UUID operationId,
    @NotNull @Positive Long accountId,
    @NotNull @Positive Long taskId,
    @NotNull Kind kind,
    @Pattern(regexp = "To Do|In Progress|Review|Done") String status,
    @Size(max = 1000) String message,
    @Valid TimeLog time) {
  public enum Kind {
    MOVE,
    COMMENT,
    LOG_TIME
  }

  public record TimeLog(
      @NotNull @Min(1) @Max(1440) Integer minutes,
      @NotNull LocalDate spentOn,
      @Size(max = 500) String note) {}

  @AssertTrue(message = "The operation must contain only the values for its kind.")
  public boolean isPayloadValid() {
    if (kind == null) return false;
    return switch (kind) {
      case MOVE -> status != null && message == null && time == null;
      case COMMENT -> status == null && message != null && !message.isBlank() && time == null;
      case LOG_TIME -> status == null && message == null && time != null;
    };
  }
}

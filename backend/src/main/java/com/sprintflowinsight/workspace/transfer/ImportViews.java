package com.sprintflowinsight.workspace.transfer;

import java.time.Instant;
import java.util.*;

public final class ImportViews {
  private ImportViews() {}

  public record Row(
      int rowNumber,
      String title,
      String description,
      String status,
      String sourceStatus,
      int storyPoints,
      String assigneeEmail,
      List<String> labels,
      Instant dueAt,
      String sourceKey,
      Instant createdAt,
      Instant startedAt,
      Instant completedAt,
      Map<Long, String> customValues,
      List<String> errors,
      List<String> warnings,
      boolean duplicate) {}

  public record Preview(
      ImportFormat format,
      List<String> columns,
      Map<String, String> mapping,
      List<Row> rows,
      String fingerprint,
      int createCount,
      int skipCount,
      boolean canCommit,
      UUID previousBatchId) {}

  public record Result(
      UUID batchId, int createdCount, int skippedCount, List<Long> taskIds, boolean replayed) {}

  public record Batch(
      UUID id, String format, int createdCount, int skippedCount, Instant createdAt) {}
}

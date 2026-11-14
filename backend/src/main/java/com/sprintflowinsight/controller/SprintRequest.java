package com.sprintflowinsight.controller;

import com.sprintflowinsight.model.Sprint;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** The fields a client may set on a sprint. */
public record SprintRequest(
    @NotBlank(message = "Sprint name is required.")
        @Size(max = 255, message = "Sprint name can be at most 255 characters.")
        String name,
    @Size(max = 255, message = "Sprint goal can be at most 255 characters.") String goal,
    String status,
    @Min(value = 0, message = "Story points cannot be negative.") int totalStoryPoints,
    Long projectId,
    LocalDate startDate,
    LocalDate endDate) {

  public Sprint toEntity() {
    Sprint sprint = new Sprint(name.trim(), goal, status, totalStoryPoints);
    sprint.setProjectId(projectId);
    sprint.setStartDate(startDate);
    sprint.setEndDate(endDate);
    return sprint;
  }
}

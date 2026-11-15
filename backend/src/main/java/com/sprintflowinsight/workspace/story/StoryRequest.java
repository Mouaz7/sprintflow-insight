package com.sprintflowinsight.workspace.story;


import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The fields a client may set on a user story. Backlog position is managed by reordering. */
public record StoryRequest(
    @NotBlank(message = "Story title is required.")
        @Size(max = 255, message = "Story title can be at most 255 characters.")
        String title,
    @Size(max = 255, message = "Description can be at most 255 characters.") String description,
    @NotBlank(message = "Acceptance criteria are required.")
        @Size(max = 255, message = "Acceptance criteria can be at most 255 characters.")
        String acceptanceCriteria,
    @Min(value = 0, message = "Story points cannot be negative.")
        @Max(value = 100, message = "Story points cannot exceed 100.")
        int storyPoints,
    @Min(value = 1, message = "Priority must be between 1 and 3.")
        @Max(value = 3, message = "Priority must be between 1 and 3.")
        int priorityId,
    @Min(value = 1, message = "Business value must be between 1 and 5.")
        @Max(value = 5, message = "Business value must be between 1 and 5.")
        Integer businessValue,
    @NotNull(message = "Project is required.") Long projectId,
    Long sprintId) {

  public UserStory toEntity() {
    UserStory story = new UserStory();
    story.setTitle(title.trim());
    story.setDescription(description);
    story.setAcceptanceCriteria(acceptanceCriteria);
    story.setStoryPoints(storyPoints);
    story.setPriorityId(priorityId);
    story.setBusinessValue(businessValue);
    story.setProjectId(projectId);
    story.setSprintId(sprintId);
    return story;
  }
}

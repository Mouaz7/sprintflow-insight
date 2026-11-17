package com.sprintflowinsight.workspace.roadmap;

/** Moves a story into an epic, or out of every epic when {@code epicId} is {@code null}. */
public record StoryEpicRequest(Long epicId) {}

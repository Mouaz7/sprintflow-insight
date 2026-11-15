package com.sprintflowinsight.workspace.events;

/** Synchronous cleanup hook; listeners must join the project deletion transaction. */
public record ProjectDeleting(Long projectId) {}

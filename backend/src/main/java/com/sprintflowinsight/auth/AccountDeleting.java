package com.sprintflowinsight.auth;

/** Must be handled in the caller's transaction; failure prevents account deletion. */
public record AccountDeleting(Long userId, String email) {}

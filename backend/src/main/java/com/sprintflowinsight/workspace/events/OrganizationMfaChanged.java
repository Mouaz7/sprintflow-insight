package com.sprintflowinsight.workspace.events;

/** A change to an organization's sign-in requirements. */
public record OrganizationMfaChanged(long organizationId, boolean required) {}

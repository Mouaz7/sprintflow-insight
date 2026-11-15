package com.sprintflowinsight.auth;

/** Reference updates join the profile transaction, before its new identity becomes visible. */
public record UserEmailChanged(String previousEmail, String nextEmail) {}

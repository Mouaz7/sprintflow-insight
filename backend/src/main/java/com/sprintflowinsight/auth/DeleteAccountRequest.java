package com.sprintflowinsight.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
    @NotBlank(message = "Email is required.") @Email(message = "Email must be valid.") String email,
    @NotBlank(message = "Current password is required.") String currentPassword) {}

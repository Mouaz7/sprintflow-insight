package com.sprintflowinsight.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank(message = "Email is required.") @Email(message = "Email must be valid.") String email,
        @NotBlank(message = "Current password is required.") String currentPassword,
        @NotBlank(message = "New password is required.") @Size(min = 6, message = "Password must contain at least 6 characters.") String newPassword) {
}

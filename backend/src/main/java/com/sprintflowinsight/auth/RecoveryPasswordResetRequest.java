package com.sprintflowinsight.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecoveryPasswordResetRequest(
        @NotBlank(message = "Email is required.") @Email(message = "Email must be valid.") String email,
        @NotBlank(message = "Recovery code is required.") String recoveryCode,
        @NotBlank(message = "New password is required.") @Size(min = 6, message = "New password must contain at least 6 characters.") String newPassword) {
}

package com.sprintflowinsight.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank(message = "Current email is required.") @Email(message = "Current email must be valid.") String currentEmail,
        @NotBlank(message = "Email is required.") @Email(message = "Email must be valid.") String email,
        @NotBlank(message = "Name is required.") @Size(max = 80, message = "Name must be 80 characters or fewer.") String displayName,
        @Size(max = 80, message = "Role must be 80 characters or fewer.") String jobTitle,
        @Size(max = 500, message = "About text must be 500 characters or fewer.") String bio,
        @Size(max = 1_000_000, message = "Profile image is too large.") String avatarImage,
        @Size(min = 6, max = 100, message = "Recovery code must contain 6 to 100 characters.") String recoveryCode) {
}

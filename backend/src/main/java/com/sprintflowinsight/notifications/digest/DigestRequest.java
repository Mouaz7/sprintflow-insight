package com.sprintflowinsight.notifications.digest;

import jakarta.validation.constraints.*;

public record DigestRequest(
    @NotNull DigestFrequency frequency, @NotBlank @Size(max = 64) String timeZone) {}

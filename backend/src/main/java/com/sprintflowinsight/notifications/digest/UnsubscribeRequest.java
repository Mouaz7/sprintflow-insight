package com.sprintflowinsight.notifications.digest;

import jakarta.validation.constraints.*;

public record UnsubscribeRequest(@NotBlank @Size(max = 2048) String token) {}

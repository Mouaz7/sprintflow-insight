package com.sprintflowinsight.auth;

import org.springframework.web.bind.annotation.*;

public record Credentials(
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String email,
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 6, max = 72)
        String password,
    @jakarta.validation.constraints.Size(max = 80) String displayName) {}

package com.sprintflowinsight.workspace.project;

import java.util.*;
import org.springframework.web.bind.annotation.*;

public record OwnershipRequest(
    @jakarta.validation.constraints.NotBlank(
            message = "Choose a project member to receive ownership.")
        @jakarta.validation.constraints.Email(message = "Enter a valid email address.")
        String email) {}

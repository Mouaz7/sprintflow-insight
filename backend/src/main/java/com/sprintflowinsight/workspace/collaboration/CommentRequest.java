package com.sprintflowinsight.workspace.collaboration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

public record CommentRequest(
    @NotBlank(message = "Comment cannot be empty.")
        @Size(max = 1000, message = "Comments must be 1000 characters or fewer.")
        String message,
    Long version) {}

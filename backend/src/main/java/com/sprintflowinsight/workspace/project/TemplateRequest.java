package com.sprintflowinsight.workspace.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

public record TemplateRequest(
    @NotBlank @Size(max = 255) String name,
    @Size(max = 255) String description,
    @NotBlank String template,
    String language) {}

package com.sprintflowinsight.workspace.transfer;

import jakarta.validation.constraints.*;
import java.util.Map;

public record ImportRequest(
    @NotNull ImportFormat format,
    @NotBlank @Size(max = 1_000_000) String content,
    @NotNull @Size(max = 32)
        Map<@NotBlank @Size(max = 80) String, @NotBlank @Size(max = 160) String> mapping,
    @NotNull @Size(max = 40)
        Map<@NotBlank @Size(max = 80) String, @NotBlank @Size(max = 20) String> statuses,
    boolean truncateText,
    boolean preserveHistory,
    @Pattern(regexp = "[a-f0-9]{64}") String expectedFingerprint) {}

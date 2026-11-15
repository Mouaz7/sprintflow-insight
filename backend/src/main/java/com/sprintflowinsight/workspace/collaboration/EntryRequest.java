package com.sprintflowinsight.workspace.collaboration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.*;
import org.springframework.web.bind.annotation.*;

public record EntryRequest(
    Long sprintId,
    @NotBlank String category,
    @NotBlank @Size(max = 1000) String message,
    Long version) {}

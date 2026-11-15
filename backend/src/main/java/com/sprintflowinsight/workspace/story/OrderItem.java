package com.sprintflowinsight.workspace.story;

import org.springframework.web.bind.annotation.*;

public record OrderItem(@jakarta.validation.constraints.NotNull Long id, Long version) {}

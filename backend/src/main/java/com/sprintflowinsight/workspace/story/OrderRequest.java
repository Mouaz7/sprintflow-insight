package com.sprintflowinsight.workspace.story;

import java.util.List;
import org.springframework.web.bind.annotation.*;

public record OrderRequest(
    @jakarta.validation.constraints.NotNull @jakarta.validation.Valid
        List<@jakarta.validation.constraints.NotNull @jakarta.validation.Valid OrderItem> items) {}

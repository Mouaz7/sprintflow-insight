package com.sprintflowinsight.workspace.attachment;

import java.util.*;
import org.springframework.web.bind.annotation.*;

public record AttachmentRequest(
    @jakarta.validation.constraints.Size(
            max = 255,
            message = "Link names can be at most 255 characters.")
        String name,
    @jakarta.validation.constraints.NotBlank(message = "Enter a link.")
        @jakarta.validation.constraints.Size(
            max = 2000,
            message = "Links can be at most 2000 characters.")
        String url) {}

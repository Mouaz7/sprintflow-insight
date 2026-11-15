package com.sprintflowinsight.workspace.lifecycle;

import java.util.*;
import org.springframework.web.bind.annotation.*;

public record TrashRequest(
    @jakarta.validation.constraints.NotNull(message = "State whether to restore the item.")
        Boolean restore) {}

package com.sprintflowinsight.workspace.lifecycle;

import java.util.*;
import org.springframework.web.bind.annotation.*;

public record ArchiveRequest(
    @jakarta.validation.constraints.NotNull(message = "State whether the project is archived.")
        Boolean archived) {}

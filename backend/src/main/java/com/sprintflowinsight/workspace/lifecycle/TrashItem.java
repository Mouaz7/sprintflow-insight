package com.sprintflowinsight.workspace.lifecycle;

import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;

public record TrashItem(
    String kind, Long id, Long projectId, String title, Instant trashedAt, Long version) {}

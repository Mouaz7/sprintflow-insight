package com.sprintflowinsight.reporting;

import java.security.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

/** Optional sprint to scope the snapshot to; omitted means the whole project. */
public record ReportRequest(@jakarta.validation.constraints.Positive Long sprintId) {}

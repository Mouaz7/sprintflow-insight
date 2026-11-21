/** Sprint-plan proposals use the pure CP-SAT optimizer and a scoped workspace snapshot. */
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"common :: errors", "workspace :: project", "workspace :: time"})
package com.sprintflowinsight.planning;

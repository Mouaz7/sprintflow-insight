/**
 * Sprint planning support (C1). The {@code optimizer} package is a pure domain service without
 * persistence or web dependencies. {@link com.sprintflowinsight.planning.SprintPlanningInputs}
 * adapts the workspace backlog (business value and story precedences from B3) into optimiser input.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"workspace :: roadmap"})
package com.sprintflowinsight.planning;

/**
 * Sprint planning support (C1). The {@code optimizer} package is a pure domain service without
 * persistence or web dependencies; adapters that read backlogs will depend on other modules later.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {})
package com.sprintflowinsight.planning;

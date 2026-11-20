@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
      "auth",
      "common",
      "common :: errors",
      "common :: audit",
      "flow",
      "workspace :: project",
      "workspace :: task",
      "workspace :: sprint",
      "workspace :: story",
      "workspace :: roadmap",
      "workspace :: customfields",
      "workspace :: activity",
      "workspace :: events",
      "forecast"
    })
package com.sprintflowinsight.reporting;

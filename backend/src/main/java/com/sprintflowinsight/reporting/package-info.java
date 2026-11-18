@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
      "auth",
      "common",
      "common :: errors",
      "flow",
      "workspace :: project",
      "workspace :: task",
      "workspace :: sprint",
      "workspace :: story",
      "workspace :: roadmap",
      "workspace :: activity",
      "workspace :: events",
      "forecast"
    })
package com.sprintflowinsight.reporting;

@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
      "auth",
      "common",
      "common :: errors",
      "workspace :: project",
      "workspace :: task",
      "workspace :: sprint",
      "workspace :: story",
      "workspace :: activity",
      "workspace :: events",
      "forecast"
    })
package com.sprintflowinsight.reporting;

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
      "workspace :: activity",
      "workspace :: events"
    })
package com.sprintflowinsight.reporting;

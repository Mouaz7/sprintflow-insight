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
      "workspace :: events"
    })
package com.sprintflowinsight.reporting;

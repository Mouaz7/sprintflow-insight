@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
      "auth",
      "common",
      "common :: crypto",
      "common :: errors",
      "workspace :: project",
      "workspace :: task",
      "workspace :: events"
    })
package com.sprintflowinsight.notifications;

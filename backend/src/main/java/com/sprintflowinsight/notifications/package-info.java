@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
      "auth",
      "common",
      "workspace :: project",
      "workspace :: task",
      "workspace :: events"
    })
package com.sprintflowinsight.notifications;

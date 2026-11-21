@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
      "common",
      "common :: crypto",
      "common :: errors",
      "workspace :: events",
      "workspace :: project"
    })
package com.sprintflowinsight.integration;

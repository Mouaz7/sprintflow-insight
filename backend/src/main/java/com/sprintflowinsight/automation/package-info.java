@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
      "common :: crypto",
      "common :: errors",
      "workspace :: collaboration",
      "workspace :: customfields",
      "workspace :: events",
      "workspace :: project",
      "workspace :: task"
    })
package com.sprintflowinsight.automation;

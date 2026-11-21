/**
 * Late-task risk model (C3): the probability that a task in the active sprint is not done when the
 * sprint ends, with the three strongest reasons.
 *
 * <p>The model is pure Java. The live adapters read project data and run scheduled training. The
 * feature vector ({@link RiskFeatures}) excludes personal identity and uses properties of the work
 * item, its column and the sprint. See {@code docs/metrics-definition.md} and ADR 0012.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"workspace :: project"})
package com.sprintflowinsight.risk;

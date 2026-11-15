/**
 * Late-task risk model (C3): the probability that a task in the active sprint is not done when
 * the sprint ends, with the three strongest reasons.
 *
 * <p>Pure Java with no persistence or Spring beans. The feature vector ({@link RiskFeatures})
 * deliberately excludes assignee, reporter or any other personal identity (fairness principle);
 * only properties of the work item, its column and the sprint are used. See {@code
 * docs/metrics-definition.md} and ADR 0012.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {})
package com.sprintflowinsight.risk;

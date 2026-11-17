/**
 * Pure scheduling logic for epics and task dependencies (B3): DFS cycle detection, critical path
 * (CPM), PERT three-point estimates, the standard normal distribution and working-day calendars.
 * No persistence, web or Spring dependencies, so everything here is unit-, property- and
 * mutation-tested in isolation. See ADR 0018.
 */
package com.sprintflowinsight.workspace.roadmap.schedule;

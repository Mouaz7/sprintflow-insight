/**
 * Epics, roadmap, task dependencies and PERT forecasts (B3). Controllers delegate to {@link
 * com.sprintflowinsight.workspace.roadmap.EpicService}, {@link
 * com.sprintflowinsight.workspace.roadmap.DependencyService} and {@link
 * com.sprintflowinsight.workspace.roadmap.RoadmapService}; the scheduling algorithms live in the
 * pure {@code schedule} package. {@link com.sprintflowinsight.workspace.roadmap.PlanningBacklog} is
 * the read interface the sprint optimiser (C1) uses for business value and story precedences.
 */
@org.springframework.modulith.NamedInterface("roadmap")
package com.sprintflowinsight.workspace.roadmap;

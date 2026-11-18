/**
 * Flow analytics (B5): cumulative flow, cycle and lead time percentiles, aging work in progress,
 * an XmR process-behaviour chart for cycle time and a Little's law consistency check.
 *
 * <p>Pure Java with no Spring or persistence dependencies. Callers pass status transitions that
 * were already loaded; every calculation is deterministic. See {@code docs/metrics-definition.md}
 * and ADR 0020.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {})
package com.sprintflowinsight.flow;

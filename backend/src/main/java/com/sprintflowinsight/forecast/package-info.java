/**
 * Probabilistic delivery forecasts: predictive distributions of future throughput, derived
 * answers (completion dates, items by a date, probability to reach a target) and rolling-origin
 * backtesting for per-project model selection. Pure Java with no Spring or persistence
 * dependencies; randomness is injected so results are reproducible with a fixed seed.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {})
package com.sprintflowinsight.forecast;

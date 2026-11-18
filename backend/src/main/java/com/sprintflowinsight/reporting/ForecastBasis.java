package com.sprintflowinsight.reporting;

/** Which history a forecast is built from. */
public enum ForecastBasis {
  /** Items completed per ISO week; forecasts count remaining items. */
  THROUGHPUT,
  /** Story points delivered per completed sprint; forecasts count remaining story points. */
  VELOCITY
}

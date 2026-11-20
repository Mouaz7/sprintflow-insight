package com.sprintflowinsight.reporting;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class ForecastController {
  private final ForecastService service;

  public ForecastController(ForecastService service) {
    this.service = service;
  }

  /** Probabilistic delivery forecast; readable by every project member, including viewers. */
  @GetMapping("/forecast")
  public ForecastResponse forecast(
      @PathVariable Long projectId,
      @Valid @ModelAttribute ForecastRequest request,
      HttpSession session) {
    return service.forecast(projectId, request, session);
  }

  @GetMapping("/forecast/backtest")
  public ForecastBacktestResponse backtest(
      @PathVariable Long projectId,
      @RequestParam(defaultValue = "THROUGHPUT") ForecastBasis basis,
      HttpSession session) {
    return service.backtest(projectId, basis, session);
  }
}

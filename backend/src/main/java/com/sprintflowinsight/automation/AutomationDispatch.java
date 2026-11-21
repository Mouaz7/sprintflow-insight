package com.sprintflowinsight.automation;

import com.sprintflowinsight.workspace.events.TaskMoved;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class AutomationDispatch {
  private static final Logger LOG = LoggerFactory.getLogger(AutomationDispatch.class);
  private final AutomationRules rules;
  private final AutomationWorker worker;

  AutomationDispatch(AutomationRules rules, AutomationWorker worker) {
    this.rules = rules;
    this.worker = worker;
  }

  @ApplicationModuleListener
  public void moved(TaskMoved event) {
    dispatch(
        new AutomationSignal(
            event.eventId(),
            event.projectId(),
            event.taskId(),
            AutomationRule.TriggerKind.TASK_MOVED,
            event.previousStatus(),
            event.status(),
            null,
            event.automationTrail()));
  }

  @ApplicationModuleListener
  public void signalled(AutomationSignal event) {
    dispatch(event);
  }

  void dispatch(AutomationSignal signal) {
    for (var rule : rules.enabled(signal.projectId(), signal.kind())) {
      try {
        worker.run(signal, rule.id());
      } catch (ResponseStatusException
          | AutomationDsl.ParseException
          | DataIntegrityViolationException rejected) {
        LOG.info("Automation rule {} did not complete for event {}", rule.id(), signal.eventId());
        worker.failed(signal, rule);
      }
    }
  }
}

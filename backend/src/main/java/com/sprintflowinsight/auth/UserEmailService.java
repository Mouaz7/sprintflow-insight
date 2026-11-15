package com.sprintflowinsight.auth;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserEmailService {
  private final ApplicationEventPublisher events;

  public UserEmailService(ApplicationEventPublisher events) {
    this.events = events;
  }

  @Transactional
  public void moveReferences(String previousEmail, String nextEmail) {
    events.publishEvent(new UserEmailChanged(previousEmail, nextEmail));
  }
}

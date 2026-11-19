package com.sprintflowinsight.notifications.digest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component
public class DigestMailer {
  private final ObjectProvider<JavaMailSender> senders;
  private final String from;

  public DigestMailer(
      ObjectProvider<JavaMailSender> senders, @Value("${app.password-reset.from:}") String from) {
    this.senders = senders;
    this.from = from;
  }

  public boolean ready() {
    var sender = senders.getIfAvailable();
    if (sender == null || from.isBlank()) return false;
    if (sender instanceof JavaMailSenderImpl configured)
      return configured.getUsername() != null
          && !configured.getUsername().isBlank()
          && configured.getPassword() != null
          && !configured.getPassword().isBlank();
    return true;
  }

  public void send(String recipient, String subject, String body) {
    var mail = new SimpleMailMessage();
    mail.setFrom(from);
    mail.setTo(recipient);
    mail.setSubject(subject);
    mail.setText(body);
    senders.getObject().send(mail);
  }
}

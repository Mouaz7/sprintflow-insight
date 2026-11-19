package com.sprintflowinsight.notifications.digest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.*;

class DigestMailerTest {
  @Test
  @SuppressWarnings("unchecked")
  void checksRealCredentialsAndSendsPlainTextWithTheConfiguredIdentity() {
    ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    var sender = new JavaMailSenderImpl();
    var mailer = new DigestMailer(provider, "from@example.test");
    assertThat(mailer.ready()).isFalse();
    when(provider.getIfAvailable()).thenReturn(sender);
    assertThat(mailer.ready()).isFalse();
    sender.setUsername("from@example.test");
    assertThat(mailer.ready()).isFalse();
    sender.setPassword("test-only");
    assertThat(mailer.ready()).isTrue();
    assertThat(new DigestMailer(provider, "").ready()).isFalse();
    var fake = mock(JavaMailSender.class);
    when(provider.getIfAvailable()).thenReturn(fake);
    when(provider.getObject()).thenReturn(fake);
    assertThat(mailer.ready()).isTrue();
    mailer.send("to@example.test", "Subject", "Plain text summary");
    var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(fake).send(captor.capture());
    assertThat(captor.getValue().getTo()).containsExactly("to@example.test");
    assertThat(captor.getValue().getFrom()).isEqualTo("from@example.test");
    assertThat(captor.getValue().getText()).isEqualTo("Plain text summary");
  }
}

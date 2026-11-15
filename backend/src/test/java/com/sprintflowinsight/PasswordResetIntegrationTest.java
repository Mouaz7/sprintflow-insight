package com.sprintflowinsight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import com.sprintflowinsight.auth.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@org.springframework.context.annotation.Import(
    com.sprintflowinsight.support.PostgresTestContainer.class)
@SpringBootTest(
    properties = {
      "app.security.auth-rate-limit.enabled=false",
      "app.password-reset.from=qa@example.com"
    })
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwords;
  @MockitoBean JavaMailSender mail;

  @Test
  void resetLinkUpdatesTheHashAndCannotBeReused() throws Exception {
    String email = "reset-" + UUID.randomUUID() + "@example.com";
    mvc.perform(
            post("/api/auth/register")
                .with(csrf())
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "email",
                            email,
                            "password",
                            "old-password-123",
                            "displayName",
                            "Reset QA"))))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/auth/password/reset/request")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email))))
        .andExpect(status().isOk());
    ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mail).send(message.capture());
    assertThat(message.getValue().getTo()).containsExactly(email);
    String text = message.getValue().getText();
    String link = "/reset-password?token=";
    assertThat(text).contains(link);
    String token = text.substring(text.indexOf(link) + link.length()).trim();
    String request =
        json.writeValueAsString(Map.of("token", token, "newPassword", "new-password-123"));
    mvc.perform(
            post("/api/auth/password/reset/confirm")
                .with(csrf())
                .contentType("application/json")
                .content(request))
        .andExpect(status().isOk());
    assertThat(
            passwords.matches(
                "new-password-123", users.findByEmail(email).orElseThrow().getPassword()))
        .isTrue();
    assertThat(
            passwords.matches(
                "old-password-123", users.findByEmail(email).orElseThrow().getPassword()))
        .isFalse();
    mvc.perform(
            post("/api/auth/password/reset/confirm")
                .with(csrf())
                .contentType("application/json")
                .content(request))
        .andExpect(status().isBadRequest());
  }

  @Test
  void invalidResetLinkAndShortPasswordAreRejected() throws Exception {
    for (String password : new String[] {"new-password-123", "short"}) {
      mvc.perform(
              post("/api/auth/password/reset/confirm")
                  .with(csrf())
                  .contentType("application/json")
                  .content(
                      json.writeValueAsString(Map.of("token", "invalid", "newPassword", password))))
          .andExpect(status().isBadRequest());
    }
  }
}

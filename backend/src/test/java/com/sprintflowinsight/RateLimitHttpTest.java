package com.sprintflowinsight;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@org.springframework.context.annotation.Import(com.sprintflowinsight.support.PostgresTestContainer.class)
@SpringBootTest(
    properties = {
      "app.security.auth-rate-limit.enabled=true"
    })
@AutoConfigureMockMvc
class RateLimitHttpTest {
  @Autowired MockMvc mvc;

  @Test
  void rejectsTheThirtyFirstLoginBeforeAuthentication() throws Exception {
    for (int attempt = 0; attempt < 30; attempt++) {
      mvc.perform(
              post("/api/auth/login")
                  .with(csrf())
                  .contentType("application/json")
                  .content(
                      "{\"email\":\"missing@example.com\",\"password\":\"incorrect-password\"}"))
          .andExpect(status().isUnauthorized());
    }
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content("{\"email\":\"missing@example.com\",\"password\":\"incorrect-password\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"));
  }
}

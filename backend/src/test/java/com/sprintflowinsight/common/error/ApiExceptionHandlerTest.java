package com.sprintflowinsight.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sprintflowinsight.ApiExceptionHandler;
import com.sprintflowinsight.auth.filter.DatabaseUnavailableFilter;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Every failure path must produce the same RFC 9457 shape with a stable catalogue code. */
class ApiExceptionHandlerTest {
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mvc =
        MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new ApiExceptionHandler())
            .setValidator(validator)
            .build();
  }

  @Test
  void unreadableBodyIsAMalformedRequest() throws Exception {
    expectProblem(
            mvc.perform(
                post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{not json")),
            ErrorCode.MALFORMED_REQUEST)
        .andExpect(jsonPath("$.detail", containsString("could not be read")))
        .andExpect(jsonPath("$.instance").value("/test/body"));
  }

  @Test
  void beanValidationListsEveryRejectedField() throws Exception {
    expectProblem(
            mvc.perform(
                post("/test/body")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"\"}")),
            ErrorCode.VALIDATION_FAILED)
        .andExpect(jsonPath("$.errors[0].field").value("name"))
        .andExpect(jsonPath("$.message").value("Name is required."));
  }

  @Test
  void missingAndMistypedParametersAreNamed() throws Exception {
    expectProblem(mvc.perform(get("/test/param")), ErrorCode.MISSING_PARAMETER)
        .andExpect(jsonPath("$.detail").value("Required parameter 'projectId' is missing."));
    expectProblem(
            mvc.perform(get("/test/param").param("projectId", "abc")), ErrorCode.INVALID_PARAMETER)
        .andExpect(jsonPath("$.detail").value("Parameter 'projectId' has an invalid value."));
  }

  @Test
  void domainExceptionsKeepTheirCodeAndMessage() throws Exception {
    expectProblem(mvc.perform(get("/test/not-found")), ErrorCode.NOT_FOUND)
        .andExpect(jsonPath("$.detail").value("Task not found."));
  }

  @Test
  void plainStatusExceptionsGetTheGenericCodeAndADefaultDetail() throws Exception {
    expectProblem(mvc.perform(get("/test/forbidden")), ErrorCode.FORBIDDEN)
        .andExpect(jsonPath("$.detail").value("Access denied. Refresh the page and try again."));
  }

  @Test
  void constraintViolationsAndIntegrityConflictsAreMapped() throws Exception {
    expectProblem(mvc.perform(get("/test/constraint")), ErrorCode.VALIDATION_FAILED);
    expectProblem(mvc.perform(get("/test/conflict")), ErrorCode.CONFLICT);
  }

  @Test
  void unexpectedFailuresNeverLeakInternals() throws Exception {
    expectProblem(mvc.perform(get("/test/crash")), ErrorCode.INTERNAL_ERROR)
        .andExpect(jsonPath("$.errorId").isString())
        .andExpect(content().string(not(containsString("db-password"))));
  }

  @Test
  void databaseFailuresAreRetryableProblemsEvenWhenWrapped() throws Exception {
    for (String path : new String[] {"/test/database", "/test/database-wrapped"}) {
      expectProblem(mvc.perform(get(path)), ErrorCode.SERVICE_UNAVAILABLE)
          .andExpect(header().string("Retry-After", "5"))
          .andExpect(jsonPath("$.detail").value(DatabaseUnavailableFilter.DETAIL))
          .andExpect(content().string(not(containsString("secret-driver-text"))));
    }
  }

  @Test
  void wrappedClientErrorsKeepTheirStatusInsteadOfBecoming500() throws Exception {
    expectProblem(mvc.perform(get("/test/wrapped")), ErrorCode.BAD_REQUEST)
        .andExpect(jsonPath("$.detail").value("Invalid export filters."));
  }

  @Test
  void unsupportedMediaTypeIsAProblemToo() throws Exception {
    expectProblem(
        mvc.perform(post("/test/body").contentType(MediaType.TEXT_PLAIN).content("x")),
        ErrorCode.UNSUPPORTED_MEDIA_TYPE);
  }

  @Test
  void typeUrisAreStableAndKebabCased() {
    org.assertj.core.api.Assertions.assertThat(ErrorCode.VALIDATION_FAILED.type().toString())
        .isEqualTo("https://sprintflow.dev/problems/validation-failed");
    org.assertj.core.api.Assertions.assertThat(ErrorCode.forStatus(HttpStatus.I_AM_A_TEAPOT))
        .isEqualTo(ErrorCode.BAD_REQUEST);
  }

  private static ResultActions expectProblem(ResultActions result, ErrorCode code)
      throws Exception {
    return result
        .andExpect(status().is(code.status().value()))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value(code.type().toString()))
        .andExpect(jsonPath("$.title").value(code.title()))
        .andExpect(jsonPath("$.status").value(code.status().value()))
        .andExpect(jsonPath("$.code").value(code.code()))
        .andExpect(jsonPath("$.message").isString());
  }

  record Body(@NotBlank(message = "Name is required.") @Size(max = 10) String name) {}

  @RestController
  static class FailingController {
    @PostMapping("/test/body")
    String body(@Valid @RequestBody Body body) {
      return body.name();
    }

    @GetMapping("/test/param")
    String param(@RequestParam Long projectId) {
      return projectId.toString();
    }

    @GetMapping("/test/not-found")
    String notFound() {
      throw ApiException.notFound("Task not found.");
    }

    @GetMapping("/test/forbidden")
    String forbidden() {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    @GetMapping("/test/constraint")
    String constraint() {
      throw new ConstraintViolationException("invalid", Set.of());
    }

    @GetMapping("/test/conflict")
    String conflict() {
      throw new DataIntegrityViolationException("duplicate key");
    }

    @GetMapping("/test/wrapped")
    String wrapped() {
      throw new org.springframework.beans.BeanInstantiationException(
          String.class,
          "Constructor threw exception",
          new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid export filters."));
    }

    @GetMapping("/test/crash")
    String crash() {
      throw new IllegalStateException("connection failed for db-password=secret");
    }

    @GetMapping("/test/database")
    String database() {
      throw new org.springframework.dao.DataAccessResourceFailureException("secret-driver-text");
    }

    @GetMapping("/test/database-wrapped")
    String wrappedDatabase() {
      throw new org.springframework.orm.jpa.JpaSystemException(
          new RuntimeException(new java.sql.SQLException("secret-driver-text", "08001")));
    }
  }
}

package com.sprintflowinsight.workspace.customfield;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.support.ApiIntegrationTest;
import com.sprintflowinsight.workspace.project.Project;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class CustomFieldQueryIntegrationTest extends ApiIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void searchFiltersTextAndExactKeysAndCsvPreservesTypedValuesWithFormulaProtection()
      throws Exception {
    User owner = user();
    Project project = project(owner, null);
    long first = task(project, "Needle Alpha"), second = task(project, "Needle Beta");
    long text = definition(project, owner, "=CSV,reference", FieldKind.TEXT, List.of());
    long number = definition(project, owner, "Budget", FieldKind.NUMBER, List.of());
    long date = definition(project, owner, "Due", FieldKind.DATE, List.of());
    long select = definition(project, owner, "Stage", FieldKind.SELECT, List.of("Ready"));
    long person = definition(project, owner, "Reviewer", FieldKind.USER, List.of());
    String member =
        members
            .findByProjectIdAndUserId(project.getId(), owner.getId())
            .orElseThrow()
            .getId()
            .toString();
    replace(
        first,
        owner,
        Map.of(
            text,
            "=1+1",
            number,
            "9007199254740993.1",
            date,
            "2026-11-18",
            select,
            "Ready",
            person,
            member));
    replace(second, owner, Map.of(number, "-2"));
    for (String q : List.of("Needle", project.getKey() + "-1"))
      mvc.perform(
              get("/api/search")
                  .with(as(owner))
                  .param("q", q)
                  .param("projectId", project.getId().toString())
                  .param("customFieldId", Long.toString(number))
                  .param("customOperator", "GREATER_THAN")
                  .param("customValue", "0"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items.length()").value(1))
          .andExpect(jsonPath("$.items[0].id").value(first))
          .andExpect(jsonPath("$.items[0].type").value("task"));
    mvc.perform(
            get("/api/search")
                .with(as(owner))
                .param("q", project.getKey() + "-1")
                .param("projectId", project.getId().toString())
                .param("customFieldId", Long.toString(number))
                .param("customOperator", "LESS_THAN")
                .param("customValue", "0"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
    mvc.perform(
            get("/api/search")
                .with(as(owner))
                .param("q", "Needle")
                .param("customFieldId", Long.toString(number))
                .param("customOperator", "IS_SET"))
        .andExpect(status().isBadRequest());
    String csv =
        mvc.perform(
                get("/api/projects/" + project.getId() + "/export.csv")
                    .with(as(owner))
                    .param("customFieldId", Long.toString(number))
                    .param("customOperator", "GREATER_THAN")
                    .param("customValue", "0"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(csv)
        .contains(
            "\"Custom: =CSV,reference\"",
            "\"Custom: Budget\"",
            "\"'=1+1\"",
            "\"9007199254740993.1\"",
            "\"2026-11-18\"",
            "\"Ready\"",
            owner.getEmail(),
            "Needle Alpha")
        .doesNotContain("Needle Beta");
    mvc.perform(
            get("/api/projects/" + project.getId() + "/export.pdf")
                .with(as(owner))
                .param("customFieldId", Long.toString(number))
                .param("customOperator", "GREATER_THAN")
                .param("customValue", "0"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF));
  }

  @Test
  void sqlAndPurePredicatesAgreeForPreciseRangesLiteralTextAndMissingValues() throws Exception {
    User owner = user();
    Project project = project(owner, null);
    long first = task(project, "First"),
        second = task(project, "Second"),
        unset = task(project, "Unset");
    long number = definition(project, owner, "Budget", FieldKind.NUMBER, List.of());
    long text = definition(project, owner, "Reference", FieldKind.TEXT, List.of());
    long date = definition(project, owner, "Date", FieldKind.DATE, List.of());
    replace(
        first, owner, Map.of(number, "9007199254740993.1", text, "Alpha%_Ö", date, "0001-01-01"));
    replace(second, owner, Map.of(number, "-2.5", text, "alpha_value", date, "9999-12-31"));
    record Example(
        Long id,
        FieldKind kind,
        FieldOperator operator,
        String expected,
        Map<Long, String> actual,
        java.util.Set<Long> matches) {}
    var numbers = Map.of(first, "9007199254740993.1", second, "-2.5");
    var texts = Map.of(first, "Alpha%_Ö", second, "alpha_value");
    var dates = Map.of(first, "0001-01-01", second, "9999-12-31");
    var cases =
        List.of(
            new Example(
                number,
                FieldKind.NUMBER,
                FieldOperator.EQUALS,
                "9007199254740993.100",
                numbers,
                java.util.Set.of(first)),
            new Example(
                number,
                FieldKind.NUMBER,
                FieldOperator.NOT_EQUALS,
                "-2.5",
                numbers,
                java.util.Set.of(first)),
            new Example(
                number,
                FieldKind.NUMBER,
                FieldOperator.LESS_THAN,
                "0",
                numbers,
                java.util.Set.of(second)),
            new Example(
                number,
                FieldKind.NUMBER,
                FieldOperator.AT_MOST,
                "-2.5",
                numbers,
                java.util.Set.of(second)),
            new Example(
                number,
                FieldKind.NUMBER,
                FieldOperator.GREATER_THAN,
                "9007199254740993",
                numbers,
                java.util.Set.of(first)),
            new Example(
                number,
                FieldKind.NUMBER,
                FieldOperator.AT_LEAST,
                "9007199254740993.1",
                numbers,
                java.util.Set.of(first)),
            new Example(
                date,
                FieldKind.DATE,
                FieldOperator.LESS_THAN,
                "2026-11-18",
                dates,
                java.util.Set.of(first)),
            new Example(
                date,
                FieldKind.DATE,
                FieldOperator.GREATER_THAN,
                "2026-11-18",
                dates,
                java.util.Set.of(second)),
            new Example(
                date,
                FieldKind.DATE,
                FieldOperator.EQUALS,
                "0001-01-01",
                dates,
                java.util.Set.of(first)),
            new Example(
                date,
                FieldKind.DATE,
                FieldOperator.AT_LEAST,
                "9999-12-31",
                dates,
                java.util.Set.of(second)),
            new Example(
                text,
                FieldKind.TEXT,
                FieldOperator.CONTAINS,
                "%_Ö",
                texts,
                java.util.Set.of(first)),
            new Example(
                text, FieldKind.TEXT, FieldOperator.CONTAINS, "ALPHA", texts, java.util.Set.of()),
            new Example(
                text,
                FieldKind.TEXT,
                FieldOperator.IS_SET,
                null,
                texts,
                java.util.Set.of(first, second)),
            new Example(
                text,
                FieldKind.TEXT,
                FieldOperator.IS_NOT_SET,
                null,
                texts,
                java.util.Set.of(unset)));
    for (var example : cases) {
      var condition = new FieldCondition(example.id(), example.operator(), example.expected());
      var expectedByPure =
          java.util.stream.Stream.of(first, second, unset)
              .filter(id -> condition.matches(example.kind(), List.of(), example.actual().get(id)))
              .collect(java.util.stream.Collectors.toSet());
      assertThat(expectedByPure).as(example.toString()).isEqualTo(example.matches());
      var request =
          get(base(project) + "/matches")
              .with(as(owner))
              .param("fieldId", example.id().toString())
              .param("operator", example.operator().name());
      if (example.expected() != null) request.param("value", example.expected());
      var result =
          json.readTree(
              mvc.perform(request)
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString());
      var found = new java.util.HashSet<Long>();
      result.get("taskIds").forEach(id -> found.add(id.asLong()));
      assertThat(found).as(example.toString()).isEqualTo(example.matches());
    }
  }

  private long definition(
      Project project, User owner, String name, FieldKind kind, List<String> options)
      throws Exception {
    var response =
        mvc.perform(
                post(base(project))
                    .with(as(owner))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(new FieldDefinitionRequest(name, kind, options))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    return json.readTree(response.getContentAsString()).get("id").asLong();
  }

  private void replace(long task, User owner, Map<Long, String> values) throws Exception {
    var body =
        new FieldValuesRequest(
            values.entrySet().stream()
                .map(entry -> new FieldValuesRequest.Entry(entry.getKey(), entry.getValue()))
                .toList());
    mvc.perform(
            put("/api/tasks/" + task + "/custom-fields")
                .with(as(owner))
                .with(csrf())
                .header("If-Match", "\"0\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andExpect(status().isOk());
  }

  private long task(Project project, String title) {
    Integer number =
        jdbc.queryForObject(
            "select coalesce(max(number),0)+1 from task where project_id=?",
            Integer.class,
            project.getId());
    return jdbc.queryForObject(
        "insert into"
            + " task(project_id,number,title,status,is_blocked,story_points,version,created_at)"
            + " values (?, ?, ?, 'To Do',false,1,0,now()) returning id",
        Long.class,
        project.getId(),
        number,
        title);
  }

  private String base(Project project) {
    return "/api/projects/" + project.getId() + "/custom-fields";
  }
}

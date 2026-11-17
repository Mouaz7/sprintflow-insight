package com.sprintflowinsight.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.support.ApiIntegrationTest;
import com.sprintflowinsight.workspace.project.Project;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** {@code GET /api/search}: access per role, matching, ranking, paging and query cost. */
class SearchIntegrationTest extends ApiIntegrationTest {
  @Autowired private JdbcTemplate jdbc;

  /** A word no other test writes, so hits from the shared database cannot interfere. */
  private static String marker() {
    StringBuilder word = new StringBuilder("zq");
    for (char c : UUID.randomUUID().toString().replace("-", "").substring(0, 10).toCharArray())
      word.append((char) ('a' + Character.digit(c, 16)));
    return word.toString();
  }

  private MockHttpServletRequestBuilder search(String q) {
    return get("/api/search").param("q", q);
  }

  private long task(Project project, int number, String title, String description) {
    return jdbc.queryForObject(
        """
        insert into task(title, description, status, is_blocked, project_id, story_points,
                         number, created_at, version)
        values (?, ?, 'To Do', false, ?, 1, ?, now(), 0) returning id
        """,
        Long.class,
        title,
        description,
        project.getId(),
        number);
  }

  private long story(Project project, String title, String description) {
    return jdbc.queryForObject(
        """
        insert into user_stories(title, description, acceptance_criteria, story_points,
                                 priority_id, project_id, created_at, version)
        values (?, ?, 'Done when shipped', 3, 2, ?, now(), 0) returning id
        """,
        Long.class,
        title,
        description,
        project.getId());
  }

  private long sprint(Project project, String name, String goal) {
    return jdbc.queryForObject(
        """
        insert into sprint(name, goal, status, total_story_points, project_id, created_at, version)
        values (?, ?, 'Planned', 0, ?, now(), 0) returning id
        """,
        Long.class,
        name,
        goal,
        project.getId());
  }

  private long comment(Project project, long taskId, String message) {
    return jdbc.queryForObject(
        """
        insert into task_comment(task_id, project_id, author_email, message, created_at, version)
        values (?, ?, 'author@example.test', ?, now(), 0) returning id
        """,
        Long.class,
        taskId,
        project.getId(),
        message);
  }

  private long epic(Project project, String title, String description) {
    return jdbc.queryForObject(
        "insert into epic(project_id,title,description,created_at) values (?,?,?,now()) returning"
            + " id",
        Long.class,
        project.getId(),
        title,
        description);
  }

  @Test
  void epicsUseTheSameRoleAndLifecycleBoundaryAsOtherSearchHits() throws Exception {
    User owner = user();
    User admin = user();
    User member = user();
    User viewer = user();
    User outsider = user();
    Project project = project(owner, member);
    member(project, admin, "Admin");
    member(project, viewer, "Viewer");
    String word = marker();
    long epic = epic(project, "Epic " + word, "Deliver a useful increment");
    for (User reader : List.of(owner, admin, member, viewer))
      mvc.perform(search(word).param("types", "epic").with(as(reader)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items.length()").value(1))
          .andExpect(jsonPath("$.items[0].type").value("epic"))
          .andExpect(jsonPath("$.items[0].id").value(epic));
    mvc.perform(search(word).with(as(outsider)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
    mvc.perform(search(word).param("projectId", project.getId().toString()).with(as(outsider)))
        .andExpect(status().isForbidden());
    project.setArchivedAt(Instant.now());
    project = projects.save(project);
    mvc.perform(search(word).param("types", "epic").with(as(owner)))
        .andExpect(jsonPath("$.items[0].id").value(epic));
    project.setTrashedAt(Instant.now());
    project = projects.save(project);
    mvc.perform(search(word).param("types", "epic").with(as(owner)))
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void epicsHaveBilingualStemsPrefixTyposHighlightsAndKeysetPaging() throws Exception {
    User owner = user();
    Project project = project(owner, null);
    String word = marker();
    long first = epic(project, "Inloggningen " + word, "Users are redirected after sign-in");
    mvc.perform(search("inloggning " + word).param("types", "epic").with(as(owner)))
        .andExpect(jsonPath("$.items[0].id").value(first));
    mvc.perform(search("redirect " + word).param("types", "epic").with(as(owner)))
        .andExpect(jsonPath("$.items[0].id").value(first))
        .andExpect(jsonPath("$.items[0].snippet[?(@.match == true)].text").isNotEmpty());
    mvc.perform(search(word.substring(0, 6)).param("types", "epic").with(as(owner)))
        .andExpect(jsonPath("$.items[0].id").value(first));
    String typo = word.substring(0, word.length() - 2) + word.charAt(word.length() - 1);
    mvc.perform(search(typo).param("types", "epic").with(as(owner)))
        .andExpect(jsonPath("$.items[0].id").value(first));
    long second = epic(project, "Inloggningen " + word, "Second increment");
    String response =
        mvc.perform(search(word).param("types", "epic").param("limit", "1").with(as(owner)))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
    Number seen = JsonPath.read(response, "$.items[0].id");
    String cursor = JsonPath.read(response, "$.nextCursor");
    String next =
        mvc.perform(
                search(word)
                    .param("types", "epic")
                    .param("limit", "1")
                    .param("cursor", cursor)
                    .with(as(owner)))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.nextCursor").doesNotExist())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Number other = JsonPath.read(next, "$.items[0].id");
    assertThat(List.of(seen.longValue(), other.longValue()))
        .containsExactlyInAnyOrder(first, second);
  }

  @Test
  void everyRoleSeesHitsInItsProjectsButOutsidersNever() throws Exception {
    String word = marker();
    User owner = user();
    User admin = user();
    User member = user();
    User viewer = user();
    User outsider = user();
    Project project = project(owner, member);
    member(project, admin, "Admin");
    member(project, viewer, "Viewer");
    task(project, 1, "Release " + word, null);

    for (User reader : List.of(owner, admin, member, viewer))
      mvc.perform(search(word).with(as(reader)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items.length()").value(1))
          .andExpect(jsonPath("$.items[0].type").value("task"))
          .andExpect(jsonPath("$.items[0].key").value(project.getKey() + "-1"));

    mvc.perform(search(word).with(as(outsider)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.nextCursor").doesNotExist());
    mvc.perform(search(word).param("projectId", project.getId().toString()).with(as(outsider)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    // An exact key from a foreign project is not revealed either.
    mvc.perform(search(project.getKey() + "-1").with(as(outsider)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void archivedProjectsStaySearchableWhileTrashedProjectsAndItemsDisappear() throws Exception {
    String word = marker();
    User owner = user();
    Project archived = project(owner, null);
    archived.setArchivedAt(Instant.now());
    projects.save(archived);
    task(archived, 1, "Archived " + word, null);

    Project trashed = project(owner, null);
    trashed.setTrashedAt(Instant.now());
    projects.save(trashed);
    task(trashed, 1, "Trashed project " + word, null);

    Project active = project(owner, null);
    long trashedTask = task(active, 1, "Trashed task " + word, null);
    jdbc.update("update task set trashed_at = now() where id = ?", trashedTask);
    comment(active, trashedTask, "Comment on a trashed task " + word);

    mvc.perform(search(word).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].projectId").value(archived.getId()));
    mvc.perform(search(word).param("projectId", trashed.getId().toString()).with(as(owner)))
        .andExpect(status().isNotFound());
  }

  @Test
  void anExactTaskKeyComesFirstAndOnlyOnce() throws Exception {
    User owner = user();
    Project project = project(owner, null);
    String key = project.getKey();
    long target = task(project, 12, "Target", "Mentions " + key + " 12 in the text");
    task(project, 120, "Other " + key + " 12 note", null);

    String body =
        mvc.perform(search(key.toLowerCase() + "-12").with(as(owner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(target))
            .andExpect(jsonPath("$.items[0].key").value(key + "-12"))
            .andExpect(jsonPath("$.items[0].exactMatch").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<Integer> ids = JsonPath.read(body, "$.items[?(@.type == 'task')].id");
    assertThat(ids).filteredOn(id -> id == target).hasSize(1);
    List<Boolean> exact = JsonPath.read(body, "$.items[1:].exactMatch");
    assertThat(exact).doesNotContain(true);
  }

  @Test
  void matchesStemsPrefixesTyposAndEveryKind() throws Exception {
    User owner = user();
    String word = marker();
    Project project = project(owner, null);
    long login = task(project, 1, "Inloggningen " + word + " misslyckas", null);
    task(project, 2, "Users are redirected after sign-in " + word, "Redirects break " + word);
    long story = story(project, "Story " + word, "Planera sprintarna");
    long sprint = sprint(project, "Sprint " + word, "Ship the release");
    long comment = comment(project, login, "Reproduced on staging " + word);

    // Swedish stemming: "inloggning" reaches "Inloggningen".
    mvc.perform(search("inloggning " + word).with(as(owner)))
        .andExpect(jsonPath("$.items[0].id").value(login));
    // English stemming: "redirect" reaches "Redirects"/"redirected".
    mvc.perform(search("redirect " + word).with(as(owner)))
        .andExpect(
            jsonPath("$.items[0].title").value("Users are redirected after sign-in " + word));
    // Prefix: the first letters of a word are enough.
    mvc.perform(search(word.substring(0, 6)).with(as(owner)).param("types", "story"))
        .andExpect(jsonPath("$.items[?(@.id == " + story + ")]").isNotEmpty());
    // Typo tolerance through trigram similarity on the title.
    String typo = word.substring(0, word.length() - 2) + word.charAt(word.length() - 1);
    mvc.perform(search(typo).with(as(owner)).param("types", "sprint"))
        .andExpect(jsonPath("$.items[?(@.id == " + sprint + ")]").isNotEmpty());
    // Comments lead to their task; the snippet marks the matched word.
    mvc.perform(search("staging " + word).with(as(owner)).param("types", "comment"))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(comment))
        .andExpect(jsonPath("$.items[0].taskId").value(login))
        .andExpect(
            jsonPath("$.items[0].snippet[?(@.match == true)].text")
                .value(org.hamcrest.Matchers.hasItem("staging")));
    // Every kind is found; the type filter narrows them.
    mvc.perform(search(word).with(as(owner)))
        .andExpect(jsonPath("$.items[?(@.type == 'task')]").isNotEmpty())
        .andExpect(jsonPath("$.items[?(@.type == 'story')]").isNotEmpty())
        .andExpect(jsonPath("$.items[?(@.type == 'sprint')]").isNotEmpty())
        .andExpect(jsonPath("$.items[?(@.type == 'comment')]").isNotEmpty());
    mvc.perform(search(word).with(as(owner)).param("types", "task,comment"))
        .andExpect(jsonPath("$.items[?(@.type == 'story')]").isEmpty())
        .andExpect(jsonPath("$.items[?(@.type == 'sprint')]").isEmpty());
    mvc.perform(search(project.getName()).with(as(owner)).param("types", "project"))
        .andExpect(jsonPath("$.items[0].id").value(project.getId()));
  }

  @Test
  void keysetPagesCoverEveryHitExactlyOnce() throws Exception {
    User owner = user();
    String word = marker();
    Project project = project(owner, null);
    Set<Long> expected = new HashSet<>();
    for (int i = 1; i <= 7; i++) expected.add(task(project, i, "Page " + word, null));

    List<Long> seen = new ArrayList<>();
    String cursor = null;
    int pages = 0;
    do {
      var request = search(word).param("limit", "3").with(as(owner));
      if (cursor != null) request.param("cursor", cursor);
      String body =
          mvc.perform(request)
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      List<Number> ids = JsonPath.read(body, "$.items[*].id");
      ids.forEach(id -> seen.add(id.longValue()));
      cursor = JsonPath.read(body, "$.nextCursor");
      pages++;
    } while (cursor != null && pages < 10);

    assertThat(pages).isEqualTo(3);
    assertThat(seen).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expected);
  }

  @Test
  void searchCostsTheSameNumberOfStatementsForAnyNumberOfProjectsAndHits() throws Exception {
    User reader = user();
    String word = marker();
    Project first = project(reader, null);
    task(first, 1, "Cost " + word, null);
    long small = statementsFor(() -> mvc.perform(search(word).with(as(reader))));
    long scopedSmall =
        statementsFor(
            () ->
                mvc.perform(
                    search(word).param("projectId", first.getId().toString()).with(as(reader))));

    for (int p = 0; p < 6; p++) {
      Project project = project(user(), reader);
      for (int i = 1; i <= 5; i++) {
        long id = task(project, i, "Cost " + word, "Body " + word);
        comment(project, id, "Comment " + word);
      }
      story(project, "Cost " + word, null);
      sprint(project, "Cost " + word, null);
    }
    long large = statementsFor(() -> mvc.perform(search(word).with(as(reader))));
    long scopedLarge =
        statementsFor(
            () ->
                mvc.perform(
                    search(word).param("projectId", first.getId().toString()).with(as(reader))));

    assertThat(large).isEqualTo(small).isEqualTo(1);
    // user lookup + one access row + the search
    assertThat(scopedLarge).isEqualTo(scopedSmall).isLessThanOrEqualTo(3);
  }

  @Test
  void rejectsInvalidParametersAsProblems() throws Exception {
    User owner = user();
    mvc.perform(search(" ").with(as(owner)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    mvc.perform(search("x".repeat(201)).with(as(owner))).andExpect(status().isBadRequest());
    mvc.perform(search("a").param("limit", "51").with(as(owner)))
        .andExpect(status().isBadRequest());
    mvc.perform(search("a").param("limit", "0").with(as(owner))).andExpect(status().isBadRequest());
    mvc.perform(search("a").param("types", "user").with(as(owner)))
        .andExpect(status().isBadRequest());
    mvc.perform(search("a").param("cursor", "bm90LWEtY3Vyc29y").with(as(owner)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    mvc.perform(search("a").param("cursor", "not base64!").with(as(owner)))
        .andExpect(status().isBadRequest());
    mvc.perform(search("a").param("projectId", "999999999").with(as(owner)))
        .andExpect(status().isNotFound());
    // Punctuation only: nothing to search for, and nothing leaks.
    mvc.perform(search("'&|!:*()").with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
    mvc.perform(search("a")).andExpect(status().isUnauthorized());
  }
}

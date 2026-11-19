package com.sprintflowinsight.workspace.poker;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sprintflowinsight.auth.User;
import com.sprintflowinsight.support.ApiIntegrationTest;
import com.sprintflowinsight.workspace.project.Project;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

class PokerIntegrationTest extends ApiIntegrationTest {
  @Autowired JdbcTemplate jdbc;
  private final JsonMapper json = JsonMapper.builder().build();

  private long story(Project project) {
    return jdbc.queryForObject(
        "insert into"
            + " user_stories(project_id,title,acceptance_criteria,story_points,priority_id,version)"
            + " values (?, 'Poker story', 'Agree an estimate', 3, 2, 0) returning id",
        Long.class,
        project.getId());
  }

  private String base(Project project) {
    return "/api/projects/" + project.getId() + "/poker-sessions";
  }

  private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object body) {
    return request
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(body));
  }

  private long start(Project project, long story, User caller, String deck) throws Exception {
    return json.readTree(
            mvc.perform(
                    body(post(base(project)), Map.of("storyId", story, "deck", deck))
                        .with(as(caller)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private void vote(String path, User voter, int round, String card) throws Exception {
    mvc.perform(body(put(path + "/vote"), Map.of("round", round, "card", card)).with(as(voter)))
        .andExpect(status().isOk());
  }

  private Map<String, Object> accept(int round, int points, long version) {
    return Map.of("round", round, "storyPoints", points, "storyVersion", version);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Owner", "Admin", "Member", "Viewer", "Outsider", "Archived", "Trashed"})
  void everyEndpointChecksProjectRoleAndArchiveState(String role) throws Exception {
    var owner = user();
    var project = project(owner, null);
    long story = story(project);
    long id = start(project, story, owner, "FIBONACCI");
    String path = base(project) + "/" + id;
    vote(path, owner, 1, "5");
    var caller = owner;
    if (List.of("Admin", "Member", "Viewer", "Outsider").contains(role)) {
      caller = user();
      if (!role.equals("Outsider")) member(project, caller, role);
    }
    if (role.equals("Archived")) {
      project.setArchivedAt(Instant.now());
      projects.save(project);
    }
    if (role.equals("Trashed")) {
      project.setTrashedAt(Instant.now());
      projects.save(project);
    }
    int read = role.equals("Outsider") ? 403 : role.equals("Trashed") ? 404 : 200;
    mvc.perform(get(base(project)).with(as(caller))).andExpect(status().is(read));
    mvc.perform(get(base(project) + "?closed=true").with(as(caller))).andExpect(status().is(read));
    mvc.perform(get(path).with(as(caller))).andExpect(status().is(read));
    int write =
        role.equals("Trashed")
            ? 404
            : role.equals("Archived")
                ? 409
                : List.of("Owner", "Admin", "Member").contains(role) ? 200 : 403;
    mvc.perform(body(put(path + "/vote"), Map.of("round", 1, "card", "3")).with(as(caller)))
        .andExpect(status().is(write));
    mvc.perform(body(delete(path + "/vote"), Map.of("round", 1)).with(as(caller)))
        .andExpect(status().is(write));
    long another = story(project);
    mvc.perform(
            body(post(base(project)), Map.of("storyId", another, "deck", "TSHIRT"))
                .with(as(caller)))
        .andExpect(status().is(write == 200 ? 201 : write));
    // Keep one vote even when the owner retracted theirs above.
    jdbc.update(
        "insert into poker_vote(session_id,round,user_id,card,voted_at) values (?,1,?,'5',now()) on"
            + " conflict do nothing",
        id,
        owner.getId());
    int facilitate =
        List.of("Owner", "Admin").contains(role) ? 200 : role.equals("Member") ? 403 : write;
    mvc.perform(body(post(path + "/reveal"), Map.of("round", 1)).with(as(caller)))
        .andExpect(status().is(facilitate));
    // Denied roles must be denied before state validation on every action.
    if (facilitate != 200) {
      mvc.perform(body(post(path + "/revote"), Map.of("round", 1)).with(as(caller)))
          .andExpect(status().is(facilitate));
      mvc.perform(body(post(path + "/accept"), accept(1, 5, 0)).with(as(caller)))
          .andExpect(status().is(facilitate));
      mvc.perform(body(delete(path), Map.of("round", 1)).with(as(caller)))
          .andExpect(status().is(facilitate));
    } else {
      mvc.perform(body(post(path + "/revote"), Map.of("round", 1)).with(as(caller)))
          .andExpect(status().isOk());
      vote(path, owner, 2, "5");
      mvc.perform(body(post(path + "/reveal"), Map.of("round", 2)).with(as(caller)))
          .andExpect(status().isOk());
      mvc.perform(body(post(path + "/accept"), accept(2, 5, 0)).with(as(caller)))
          .andExpect(status().isOk());
      long cancelled = start(project, story(project), owner, "TSHIRT");
      mvc.perform(
              body(delete(base(project) + "/" + cancelled), Map.of("round", 1)).with(as(caller)))
          .andExpect(status().isOk());
    }
  }

  @Test
  void votesStaySecretAcrossReadersListsEventsAndRevotesUntilRevealed() throws Exception {
    var owner = user();
    var member = user();
    var viewer = user();
    var project = project(owner, member);
    member(project, viewer, "Viewer");
    long story = story(project);
    long id = start(project, story, member, "FIBONACCI");
    String path = base(project) + "/" + id;
    vote(path, member, 1, "8");
    for (var reader : List.of(owner, viewer)) {
      mvc.perform(get(path).with(as(reader)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.myCard").isEmpty())
          .andExpect(jsonPath("$.rounds").isEmpty())
          .andExpect(jsonPath("$.voters.length()").value(1))
          .andExpect(jsonPath("$.voters[0].card").doesNotExist());
      mvc.perform(get(base(project)).with(as(reader)))
          .andExpect(jsonPath("$[0].myCard").doesNotExist())
          .andExpect(jsonPath("$[0].rounds").doesNotExist())
          .andExpect(jsonPath("$[0].voters").doesNotExist());
    }
    mvc.perform(get(path).with(as(member))).andExpect(jsonPath("$.myCard").value("8"));
    mvc.perform(body(post(path + "/accept"), accept(1, 8, 0)).with(as(member)))
        .andExpect(status().isConflict());
    mvc.perform(body(post(path + "/reveal"), Map.of("round", 1)).with(as(member)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rounds[0].votes[0].card").value("8"))
        .andExpect(jsonPath("$.rounds[0].statistics.consensus").value(true));
    mvc.perform(body(post(path + "/revote"), Map.of("round", 1)).with(as(member)))
        .andExpect(status().isOk());
    vote(path, member, 2, "2");
    mvc.perform(get(path).with(as(viewer)))
        .andExpect(jsonPath("$.round").value(2))
        .andExpect(jsonPath("$.rounds.length()").value(1))
        .andExpect(jsonPath("$.rounds[0].number").value(1))
        .andExpect(jsonPath("$.rounds[0].votes[0].card").value("8"))
        .andExpect(jsonPath("$.myCard").isEmpty());
    for (var action : List.of("reveal", "revote"))
      mvc.perform(body(post(path + "/" + action), Map.of("round", 1)).with(as(member)))
          .andExpect(status().isConflict());
    mvc.perform(body(put(path + "/vote"), Map.of("round", 1, "card", "13")).with(as(member)))
        .andExpect(status().isConflict());
    mvc.perform(body(delete(path + "/vote"), Map.of("round", 1)).with(as(member)))
        .andExpect(status().isConflict());
    mvc.perform(body(delete(path), Map.of("round", 1)).with(as(member)))
        .andExpect(status().isConflict());
    mvc.perform(body(post(path + "/reveal"), Map.of("round", 2)).with(as(member)))
        .andExpect(status().isOk());
    mvc.perform(body(post(path + "/accept"), accept(2, 2, 0)).with(as(member)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.finalStoryPoints").value(2))
        .andExpect(jsonPath("$.canVote").value(false));
    assertThat(
            jdbc.queryForObject(
                "select story_points from user_stories where id=?", Integer.class, story))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from activity_event where project_id=? and"
                    + " event_type='STORY_ESTIMATED'",
                Integer.class,
                project.getId()))
        .isEqualTo(1);
    mvc.perform(body(post(path + "/accept"), accept(2, 2, 1)).with(as(member)))
        .andExpect(status().isConflict());
  }

  @Test
  void cancellationDeletesUnrevealedVotesAndRetainsRevealedHistory() throws Exception {
    var owner = user();
    var project = project(owner, null);
    long id = start(project, story(project), owner, "TSHIRT");
    String path = base(project) + "/" + id;
    vote(path, owner, 1, "M");
    mvc.perform(body(post(path + "/reveal"), Map.of("round", 1)).with(as(owner)))
        .andExpect(status().isOk());
    mvc.perform(body(post(path + "/revote"), Map.of("round", 1)).with(as(owner)))
        .andExpect(status().isOk());
    vote(path, owner, 2, "XL");
    mvc.perform(body(delete(path), Map.of("round", 2)).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.myCard").isEmpty())
        .andExpect(jsonPath("$.rounds.length()").value(1));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from poker_vote where session_id=? and round=2",
                Integer.class,
                id))
        .isZero();
    mvc.perform(get(base(project) + "?closed=true").with(as(owner)))
        .andExpect(jsonPath("$[0].id").value(id));
  }

  @Test
  void validatesDecksScopeRoundsCsrfAndStoryRevision() throws Exception {
    var owner = user();
    var project = project(owner, null);
    long story = story(project);
    var foreign = project(user(), null);
    long other = story(foreign);
    mvc.perform(
            body(post(base(project)), Map.of("storyId", other, "deck", "FIBONACCI"))
                .with(as(owner)))
        .andExpect(status().isBadRequest());
    for (var cards :
        List.of(List.of("1"), List.of("1", "1"), List.of("?", "☕"), List.of("1", "<img>")))
      mvc.perform(
              body(post(base(project)), Map.of("storyId", story, "deck", "CUSTOM", "cards", cards))
                  .with(as(owner)))
          .andExpect(status().isBadRequest());
    long id = start(project, story, owner, "FIBONACCI");
    String path = base(project) + "/" + id;
    mvc.perform(
            body(post(base(project)), Map.of("storyId", story, "deck", "FIBONACCI"))
                .with(as(owner)))
        .andExpect(status().isConflict());
    mvc.perform(get(base(foreign) + "/" + id).with(as(foreignOwner(foreign))))
        .andExpect(status().isNotFound());
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(
            post(path + "/reveal")
                .with(as(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"round\":1}"))
        .andExpect(status().isForbidden());
    mvc.perform(body(post(path + "/reveal"), Map.of("round", 1)).with(as(owner)))
        .andExpect(status().isConflict());
    for (var invalid :
        List.of(
            Map.of("card", "3"), Map.of("round", 0, "card", "3"), Map.of("round", 1, "card", "21")))
      mvc.perform(body(put(path + "/vote"), invalid).with(as(owner)))
          .andExpect(status().isBadRequest());
    vote(path, owner, 1, "5");
    mvc.perform(body(post(path + "/reveal"), Map.of("round", 1)).with(as(owner)))
        .andExpect(status().isOk());
    jdbc.update("update user_stories set story_points=8,version=version+1 where id=?", story);
    mvc.perform(body(post(path + "/accept"), accept(1, 5, 0)).with(as(owner)))
        .andExpect(status().isPreconditionFailed());
    assertThat(jdbc.queryForObject("select status from poker_session where id=?", String.class, id))
        .isEqualTo("REVEALED");
    mvc.perform(body(post(path + "/accept"), accept(1, 13, 1)).with(as(owner)))
        .andExpect(status().isBadRequest());
    mvc.perform(body(post(path + "/accept"), accept(1, 5, 1)).with(as(owner)))
        .andExpect(status().isOk());
    long cancelled = start(project, story, owner, "TSHIRT");
    jdbc.update("update user_stories set trashed_at=now() where id=?", story);
    mvc.perform(get(base(project) + "/" + cancelled).with(as(owner)))
        .andExpect(status().isNotFound());
    mvc.perform(get(base(project)).with(as(owner))).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void votingAndRevealAreAtomicUnderConcurrentRequests() throws Exception {
    var owner = user();
    var voter = user();
    var project = project(owner, voter);
    long id = start(project, story(project), owner, "FIBONACCI");
    String path = base(project) + "/" + id;
    vote(path, owner, 1, "3");
    var ready = new CountDownLatch(2);
    var go = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var voting =
          pool.submit(
              () -> {
                ready.countDown();
                go.await();
                return mvc.perform(
                        body(put(path + "/vote"), Map.of("round", 1, "card", "8")).with(as(voter)))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      var revealing =
          pool.submit(
              () -> {
                ready.countDown();
                go.await();
                return mvc.perform(body(post(path + "/reveal"), Map.of("round", 1)).with(as(owner)))
                    .andReturn()
                    .getResponse();
              });
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      int voteStatus = voting.get(15, TimeUnit.SECONDS);
      var revealed = revealing.get(15, TimeUnit.SECONDS);
      assertThat(voteStatus).isIn(200, 409);
      assertThat(revealed.getStatus()).isEqualTo(200);
      int expected = voteStatus == 200 ? 2 : 1;
      assertThat(
              json.readTree(revealed.getContentAsString()).get("rounds").get(0).get("votes").size())
          .isEqualTo(expected);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from poker_vote where session_id=?", Integer.class, id))
          .isEqualTo(expected);
    }
  }

  @Test
  void boundsRoundsAndRequiresAnActualEstimateForConsensus() throws Exception {
    var owner = user();
    var project = project(owner, null);
    long story = story(project);
    long id = start(project, story, owner, "TSHIRT");
    String path = base(project) + "/" + id;
    vote(path, owner, 1, "?");
    mvc.perform(body(post(path + "/reveal"), Map.of("round", 1)).with(as(owner)))
        .andExpect(status().isOk());
    mvc.perform(body(post(path + "/accept"), accept(1, 3, 0)).with(as(owner)))
        .andExpect(status().isConflict());
    mvc.perform(body(post(path + "/revote"), Map.of("round", 1)).with(as(owner)))
        .andExpect(status().isOk());
    vote(path, owner, 2, "M");
    mvc.perform(body(post(path + "/reveal"), Map.of("round", 2)).with(as(owner)))
        .andExpect(status().isOk());
    mvc.perform(body(post(path + "/accept"), accept(2, 5, 0)).with(as(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.finalValue").value("M"));
    long bounded = start(project, story, owner, "FIBONACCI");
    jdbc.update(
        "update poker_session set round=50,revealed_round=50,status='REVEALED' where id=?",
        bounded);
    mvc.perform(
            body(post(base(project) + "/" + bounded + "/revote"), Map.of("round", 50))
                .with(as(owner)))
        .andExpect(status().isConflict());
    var foreign = project(user(), null);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update poker_session set project_id=? where id=?", foreign.getId(), bounded))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    // A permanent story deletion cascades through its sessions and votes.
    jdbc.update("delete from user_stories where id=?", story);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from poker_session where id in (?,?)", Integer.class, id, bounded))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from poker_vote where session_id=?", Integer.class, id))
        .isZero();
  }

  private User foreignOwner(Project project) {
    return users.findByEmail(project.getOwnerEmail()).orElseThrow();
  }
}

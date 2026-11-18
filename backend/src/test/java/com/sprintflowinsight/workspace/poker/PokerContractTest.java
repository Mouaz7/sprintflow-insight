package com.sprintflowinsight.workspace.poker;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class PokerContractTest {
  @Test
  @SuppressWarnings("unchecked")
  void documentsAllRequestAndResponseFieldsAndRoundPreconditions() throws Exception {
    try (var input = Files.newInputStream(Path.of("..", "docs", "openapi.yaml"))) {
      Map<String, Object> document = new Yaml().load(input);
      var schemas =
          (Map<String, Map<String, Object>>)
              ((Map<?, ?>) document.get("components")).get("schemas");
      var records =
          Map.ofEntries(
              Map.entry("PokerSession", PokerSessionView.class),
              Map.entry("PokerSessionSummary", PokerSessionSummary.class),
              Map.entry("PokerStatistics", PokerStatistics.class),
              Map.entry("PokerRound", PokerSessionView.Round.class),
              Map.entry("PokerVoter", PokerSessionView.Voter.class),
              Map.entry("PokerVote", PokerSessionView.Vote.class),
              Map.entry("PokerCardCount", PokerStatistics.CardCount.class),
              Map.entry("StartPokerRequest", StartPokerRequest.class),
              Map.entry("PokerVoteRequest", VoteRequest.class),
              Map.entry("AcceptPokerRequest", AcceptPokerRequest.class),
              Map.entry("PokerRoundRequest", PokerRoundRequest.class));
      records.forEach(
          (name, type) ->
              assertThat(((Map<String, Object>) schemas.get(name).get("properties")).keySet())
                  .as(name)
                  .containsExactlyInAnyOrderElementsOf(
                      Arrays.stream(type.getRecordComponents())
                          .map(java.lang.reflect.RecordComponent::getName)
                          .toList()));
      var properties =
          (Map<String, Map<String, Object>>) schemas.get("PokerSession").get("properties");
      assertThat((java.util.List<String>) properties.get("status").get("enum"))
          .containsExactlyElementsOf(
              Arrays.stream(PokerSession.Status.values()).map(Enum::name).toList());
      assertThat((java.util.List<String>) properties.get("deck").get("enum"))
          .containsExactlyElementsOf(Arrays.stream(PokerDeck.values()).map(Enum::name).toList());
      var paths = (Map<String, Map<String, Object>>) document.get("paths");
      String base = "/api/projects/{projectId}/poker-sessions";
      for (String suffix :
          java.util.List.of(
              "",
              "/{sessionId}",
              "/{sessionId}/vote",
              "/{sessionId}/reveal",
              "/{sessionId}/revote",
              "/{sessionId}/accept")) assertThat(paths).containsKey(base + suffix);
      var accept = (Map<String, Object>) paths.get(base + "/{sessionId}/accept").get("post");
      assertThat((Map<String, Object>) accept.get("responses")).containsKey("412");
      assertThat(schemas.get("PokerSession").get("description").toString())
          .contains("never includes current unrevealed votes");
    }
  }
}

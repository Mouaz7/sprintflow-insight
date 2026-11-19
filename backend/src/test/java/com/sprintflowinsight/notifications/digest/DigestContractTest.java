package com.sprintflowinsight.notifications.digest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class DigestContractTest {
  @Test
  @SuppressWarnings("unchecked")
  void recordsCadencesAndPublicOptOutMatchOpenApi() throws Exception {
    try (var input = Files.newInputStream(Path.of("..", "docs", "openapi.yaml"))) {
      Map<String, Object> doc = new Yaml().load(input);
      var schemas =
          (Map<String, Map<String, Object>>) ((Map<?, ?>) doc.get("components")).get("schemas");
      Map.of(
              "DigestPreferenceRequest",
              DigestRequest.class,
              "DigestPreference",
              DigestResponse.class,
              "DigestUnsubscribeRequest",
              UnsubscribeRequest.class,
              "CommentRequest",
              com.sprintflowinsight.workspace.collaboration.CommentRequest.class)
          .forEach(
              (name, type) ->
                  assertThat(((Map<String, Object>) schemas.get(name).get("properties")).keySet())
                      .containsExactlyInAnyOrderElementsOf(
                          Arrays.stream(type.getRecordComponents())
                              .map(java.lang.reflect.RecordComponent::getName)
                              .toList()));
      var props =
          (Map<String, Map<String, Object>>) schemas.get("DigestPreference").get("properties");
      assertThat((List<String>) props.get("frequency").get("enum"))
          .containsExactlyElementsOf(
              Arrays.stream(DigestFrequency.values()).map(Enum::name).toList());
      var paths = (Map<String, Map<String, Object>>) doc.get("paths");
      var put = (Map<String, Object>) paths.get("/api/auth/digest").get("put");
      assertThat(put.get("parameters").toString()).contains("ifMatch");
      assertThat((Map<String, Object>) put.get("responses")).containsKeys("412", "428");
      var post = (Map<String, Object>) paths.get("/api/digest/unsubscribe").get("post");
      assertThat((List<?>) post.get("security")).isEmpty();
    }
  }
}

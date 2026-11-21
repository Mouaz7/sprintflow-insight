package com.sprintflowinsight.automation;

import com.sprintflowinsight.common.error.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

final class GithubWebhook {
  private static final Pattern COMMIT_HASH = Pattern.compile("[0-9a-fA-F]{40}");
  private static final int MAX_TITLE = 250;

  record Reference(String kind, String key, String title, String state, String text) {}

  record Delivery(String action, long pullRequestId, List<Reference> references) {}

  private GithubWebhook() {}

  static Delivery pullRequest(JsonNode payload) {
    String action = payload.path("action").asText("");
    var pull = payload.path("pull_request");
    String state =
        switch (action) {
          case "closed" -> pull.path("merged").asBoolean(false) ? "MERGED" : "CLOSED";
          case "opened", "reopened" -> "OPEN";
          case "edited", "synchronize" ->
              pull.path("merged").asBoolean(false)
                  ? "MERGED"
                  : "closed".equals(pull.path("state").asText("")) ? "CLOSED" : "OPEN";
          default -> null;
        };
    if (state == null) return new Delivery(null, 0, List.of());
    long id = pull.path("id").asLong(0);
    int number = payload.path("number").asInt(pull.path("number").asInt(0));
    if (id <= 0 || number <= 0) {
      throw ApiException.badRequest("GitHub pull request ID and number are required.");
    }
    String title = pull.path("title").asText("");
    String branch = pull.path("head").path("ref").asText("");
    var reference =
        new Reference(
            "PULL_REQUEST", Integer.toString(number), title(title), state, title + " " + branch);
    return new Delivery(action, id, List.of(reference));
  }

  static Delivery push(JsonNode payload) {
    var commits = payload.path("commits");
    if (!commits.isArray()) throw ApiException.badRequest("GitHub commits must be an array.");
    if (commits.size() > 2048) throw ApiException.badRequest("Too many GitHub commits.");
    if (payload.path("deleted").asBoolean(false)) return new Delivery(null, 0, List.of());
    String branch = payload.path("ref").asText("");
    if (!branch.startsWith("refs/heads/")) return new Delivery(null, 0, List.of());
    var references = new ArrayList<Reference>();
    for (JsonNode commit : commits) {
      String hash = commit.path("id").asText("");
      if (!COMMIT_HASH.matcher(hash).matches()) {
        throw ApiException.badRequest("GitHub commit ID must be a SHA-1 hash.");
      }
      String text = commit.path("message").asText("");
      references.add(
          new Reference(
              "COMMIT", hash.toLowerCase(Locale.ROOT), title(text), null, branch + " " + text));
    }
    return new Delivery(null, 0, List.copyOf(references));
  }

  private static String title(String text) {
    String first = text.lines().findFirst().orElse("").replace("\u0000", "").strip();
    int limit = Math.min(first.codePointCount(0, first.length()), MAX_TITLE);
    return first.substring(0, first.offsetByCodePoints(0, limit));
  }
}

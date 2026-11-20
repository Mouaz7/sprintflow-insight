package com.sprintflowinsight.workspace.transfer;

import com.sprintflowinsight.common.error.ApiException;
import java.time.Instant;
import java.util.*;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Jira REST v2 search exports and MongoDB --jsonArray exports use the same issue shape. */
public final class JiraImportParser {
  private static final JsonMapper JSON =
      JsonMapper.builder(
              JsonFactory.builder()
                  .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                  .streamReadConstraints(
                      StreamReadConstraints.builder()
                          .maxNestingDepth(32)
                          .maxStringLength(8192)
                          .maxNumberLength(64)
                          .build())
                  .build())
          .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();

  private JiraImportParser() {}

  public static ImportTable parse(String input) {
    if (input == null || input.length() > CsvImportParser.MAX_CONTENT)
      throw ApiException.badRequest("Use a Jira JSON file of at most 1 MB.");
    try {
      JsonNode root = JSON.readTree(input);
      if (root == null) throw invalid();
      JsonNode issues = root.isArray() ? root : root.path("issues");
      if (!issues.isArray() || issues.isEmpty() || issues.size() > 200) throw invalid();
      var columns = new TreeSet<String>();
      var rows = new ArrayList<Map<String, String>>();
      for (var issue : issues) {
        if (!issue.isObject() || !issue.path("fields").isObject()) throw invalid();
        var row = new LinkedHashMap<String, String>();
        flatten(issue, "", row, 0);
        row.keySet()
            .removeIf(
                k ->
                    k.startsWith("changelog.")
                        || k.startsWith("_id.")
                        || k.startsWith("self")
                        || k.startsWith("fields.comment")
                        || k.startsWith("fields.creator")
                        || k.startsWith("fields.reporter")
                        || k.startsWith("fields.assignee")
                        || k.startsWith("fields.watches")
                        || k.startsWith("fields.votes"));
        // Identity objects from public research datasets are never turned into project members.
        var f = issue.path("fields");
        row.put("fields.description", description(f.path("description"), 0));
        row.put("fields.labels", f.path("labels").isArray() ? joinLabels(f.path("labels")) : "");
        if (!row.containsKey("startedAt")) {
          String startedAt = firstStart(issue.path("changelog").path("histories"));
          if (startedAt != null) row.put("startedAt", startedAt);
        }
        if (row.size() > 256)
          throw ApiException.badRequest("A Jira issue has too many scalar fields.");
        var email = f.path("assignee").path("emailAddress");
        if (email.isTextual()) row.put("fields.assignee.emailAddress", email.asString());
        columns.addAll(row.keySet());
        if (columns.size() > 256) throw invalid();
        rows.add(row);
      }
      return new ImportTable(new ArrayList<>(columns), rows);
    } catch (tools.jackson.core.JacksonException malformed) {
      throw ApiException.badRequest("Invalid or excessively nested Jira JSON.");
    }
  }

  private static String firstStart(JsonNode histories) {
    Instant first = null;
    for (var entry : histories) {
      for (var item : entry.path("items")) {
        if (!"status".equals(item.path("field").asString(""))
            || !"In Progress".equalsIgnoreCase(item.path("toString").asString(""))) continue;
        String at = entry.path("created").asString("").strip();
        if (at.isEmpty()) continue;
        Instant instant = ImportMapper.date(at, "Started date", new ArrayList<>());
        // Keep an invalid timestamp for row-level validation instead of silently changing history.
        if (instant == null) return at;
        if (first == null || instant.isBefore(first)) first = instant;
        break;
      }
    }
    return first == null ? null : first.toString();
  }

  private static void flatten(JsonNode n, String path, Map<String, String> row, int depth) {
    if (depth > 6) return;
    if (n.isObject()) {
      for (var p : n.properties()) {
        String next = path.isEmpty() ? p.getKey() : path + "." + p.getKey();
        if (next.length() > 160) continue;
        if (next.equals("changelog")
            || next.equals("fields.comment")
            || next.equals("fields.creator")
            || next.equals("fields.reporter")
            || next.equals("fields.assignee")) continue;
        flatten(p.getValue(), next, row, depth + 1);
      }
    } else if (n.isTextual() || n.isNumber() || n.isBoolean()) {
      if (row.size() >= 256) throw invalid();
      row.put(path, n.asString());
    } else if (n.isNull()) row.put(path, "");
  }

  private static String description(JsonNode n, int depth) {
    if (depth > 16) return "";
    if (n.isTextual()) return n.asString();
    if (!n.isObject()) return "";
    StringBuilder b = new StringBuilder(n.path("text").asString(""));
    for (var child : n.path("content")) {
      b.append(description(child, depth + 1));
      if (b.length() > 8192) throw invalid();
    }
    if ("paragraph".equals(n.path("type").asString(""))) b.append('\n');
    return b.toString();
  }

  private static String joinLabels(JsonNode n) {
    var list = new ArrayList<String>();
    for (var v : n) {
      if (!v.isTextual()) throw invalid();
      list.add(v.asString());
    }
    return String.join(";", list);
  }

  private static ApiException invalid() {
    return ApiException.badRequest("Provide a Jira issues array of 1–200 objects with fields.");
  }
}

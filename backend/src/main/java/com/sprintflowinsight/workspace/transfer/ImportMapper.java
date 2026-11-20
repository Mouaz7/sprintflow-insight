package com.sprintflowinsight.workspace.transfer;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.customfield.*;
import com.sprintflowinsight.workspace.project.ProjectMember;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Pure mapping/validation shared by preview and commit. No database writes. */
final class ImportMapper {
  static final Set<String> TARGETS =
      Set.of(
          "title",
          "description",
          "status",
          "storyPoints",
          "assigneeEmail",
          "labels",
          "dueAt",
          "sourceKey",
          "createdAt",
          "startedAt",
          "completedAt");
  static final Set<String> STATUSES = Set.of("To Do", "In Progress", "Review", "Done");

  static Map<String, String> mapping(
      ImportRequest request, ImportTable table, List<CustomFieldViews.Definition> definitions) {
    Map<String, String> result = new TreeMap<>(request.mapping());
    if (result.isEmpty()) {
      Map<String, String> aliases =
          request.format() == ImportFormat.JIRA_JSON
              ? Map.ofEntries(
                  Map.entry("title", "fields.summary"),
                  Map.entry("description", "fields.description"),
                  Map.entry("status", "fields.status.name"),
                  Map.entry("sourceKey", "key"),
                  Map.entry("createdAt", "fields.created"),
                  Map.entry("startedAt", "startedAt"),
                  Map.entry("completedAt", "fields.resolutiondate"),
                  Map.entry("dueAt", "fields.duedate"),
                  Map.entry("labels", "fields.labels"),
                  Map.entry("assigneeEmail", "fields.assignee.emailAddress"))
              : Map.of();
      for (String t : TARGETS) {
        String name = aliases.getOrDefault(t, t);
        table.columns().stream()
            .filter(c -> c.equalsIgnoreCase(name) || c.replace(" ", "").equalsIgnoreCase(t))
            .findFirst()
            .ifPresent(c -> result.put(t, c));
      }
      for (var d : definitions)
        table.columns().stream()
            .filter(c -> c.equals("Custom: " + d.name()))
            .findFirst()
            .ifPresent(c -> result.put("custom:" + d.id(), c));
    }
    Set<Long> ids = new HashSet<>();
    definitions.forEach(d -> ids.add(d.id()));
    for (var e : result.entrySet()) {
      if (!TARGETS.contains(e.getKey())) {
        try {
          if (!e.getKey().startsWith("custom:")
              || !ids.contains(Long.valueOf(e.getKey().substring(7))))
            throw new IllegalArgumentException();
        } catch (RuntimeException x) {
          throw ApiException.badRequest("Map only task fields or custom fields from this project.");
        }
      }
      if (!table.columns().contains(e.getValue()))
        throw ApiException.badRequest("A mapped source column is missing from the file.");
    }
    if (request.statuses().values().stream().anyMatch(s -> !STATUSES.contains(s)))
      throw ApiException.badRequest(
          "Map each workflow state to To Do, In Progress, Review or Done.");
    return result;
  }

  static ImportViews.Row row(
      int index,
      Map<String, String> source,
      Map<String, String> mapping,
      ImportRequest request,
      List<CustomFieldViews.Definition> definitions,
      List<ProjectMember> members,
      Instant now) {
    var errors = new ArrayList<String>();
    var warnings = new ArrayList<String>();
    java.util.function.Function<String, String> value =
        t -> source.getOrDefault(mapping.getOrDefault(t, ""), "").strip();
    String title = value.apply("title"), description = value.apply("description");
    if (title.isBlank()) errors.add("Map a non-empty title.");
    title = text(title, 255, "Title", request.truncateText(), errors, warnings);
    description = text(description, 255, "Description", request.truncateText(), errors, warnings);
    String rawStatus = value.apply("status"),
        status =
            rawStatus.isEmpty() ? "To Do" : request.statuses().getOrDefault(rawStatus, rawStatus);
    if (!STATUSES.contains(status)) errors.add("Map the workflow state to an application state.");
    int points = 0;
    String rawPoints = value.apply("storyPoints");
    if (!rawPoints.isBlank())
      try {
        if (!rawPoints.matches("[0-9]{1,4}")) throw new NumberFormatException();
        points = Integer.parseInt(rawPoints);
        if (points > 1000) throw new NumberFormatException();
      } catch (NumberFormatException x) {
        errors.add("Story points must be an integer from 0 to 1000.");
      }
    String assignee = value.apply("assigneeEmail").toLowerCase(Locale.ROOT);
    if (!assignee.isEmpty()
        && members.stream().noneMatch(m -> m.getEmail().equalsIgnoreCase(assignee)))
      errors.add("Assignee must be a current project member.");
    var labels =
        Arrays.stream(value.apply("labels").split(";", -1))
            .map(String::strip)
            .filter(s -> !s.isEmpty())
            .distinct()
            .toList();
    if (labels.size() > 12
        || labels.stream().anyMatch(l -> l.length() > 32 || l.indexOf('\0') >= 0))
      errors.add("Use at most 12 labels of 1–32 characters, separated by semicolons.");
    String key = value.apply("sourceKey");
    if (key.length() > 128 || key.indexOf('\0') >= 0)
      errors.add("Source keys can have at most 128 characters.");
    if (request.format() == ImportFormat.JIRA_JSON && key.isBlank())
      errors.add("Map the Jira key so existing issues can be skipped safely.");
    Instant due = date(value.apply("dueAt"), "Due date", errors),
        created = date(value.apply("createdAt"), "Created date", errors),
        started = date(value.apply("startedAt"), "Started date", errors),
        completed = date(value.apply("completedAt"), "Completed date", errors);
    if (request.preserveHistory()) {
      if (created == null) errors.add("Preserving history requires a created date.");
      if ("To Do".equals(status) && started != null)
        errors.add("A historical To Do task must leave its started date empty.");
      if (Set.of("In Progress", "Review").contains(status) && started == null)
        errors.add("A historical active task needs a started date.");
      if (created != null
          && (created.isAfter(now)
              || started != null && (started.isBefore(created) || started.isAfter(now))
              || completed != null
                  && (completed.isBefore(created)
                      || completed.isAfter(now)
                      || started != null && completed.isBefore(started))))
        errors.add("Historical dates must be ordered and cannot be in the future.");
      if ("Done".equals(status) != (completed != null))
        errors.add(
            "A historical Done task needs a completed date; other states must leave it empty.");
      warnings.add(
          "History preserves created/started/completed milestones, not every original Jira"
              + " transition.");
    }
    var fields = new TreeMap<Long, String>();
    for (var d : definitions)
      if (mapping.containsKey("custom:" + d.id())) {
        String raw = value.apply("custom:" + d.id());
        try {
          if (d.kind() == FieldKind.USER && !raw.isBlank()) {
            final String candidate = raw;
            var member =
                members.stream()
                    .filter(
                        m ->
                            m.getId().toString().equals(candidate)
                                || m.getEmail().equalsIgnoreCase(candidate))
                    .findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Choose a current project member."));
            raw = member.getId().toString();
          }
          var parsed = FieldValue.parse(d.kind(), d.options(), raw);
          if (parsed != null) fields.put(d.id(), parsed.value());
        } catch (ApiException x) {
          errors.add(d.name() + ": " + x.getReason());
        }
      }
    return new ImportViews.Row(
        index,
        title,
        description,
        status,
        rawStatus,
        points,
        assignee.isBlank() ? null : assignee,
        labels,
        due,
        key.isBlank() ? null : key,
        created,
        started,
        completed,
        fields,
        List.copyOf(errors),
        List.copyOf(warnings),
        false);
  }

  static Instant date(String raw, String field, List<String> errors) {
    if (raw.isBlank()) return null;
    try {
      Instant at =
          raw.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
              ? LocalDate.parse(raw).atTime(23, 59, 59).toInstant(ZoneOffset.UTC)
              : OffsetDateTime.parse(
                      raw.replaceFirst("([+-][0-9]{2})([0-9]{2})$", "$1:$2"),
                      DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                  .toInstant();
      int year = at.atOffset(ZoneOffset.UTC).getYear();
      if (year < 1900 || year > 9999) throw new DateTimeException("year");
      return at;
    } catch (DateTimeException x) {
      errors.add(field + " must be a valid ISO date or offset timestamp, year 1900–9999.");
      return null;
    }
  }

  private static String text(
      String value,
      int max,
      String field,
      boolean truncate,
      List<String> errors,
      List<String> warnings) {
    if (value.indexOf('\0') >= 0) errors.add(field + " cannot contain null characters.");
    if (value.length() > max) {
      if (truncate) {
        warnings.add(
            field
                + " was explicitly shortened to "
                + max
                + " characters; original text remains in import provenance.");
        int end = max;
        if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
      }
      errors.add(
          field + " exceeds " + max + " characters; shorten it or explicitly allow truncation.");
    }
    return value;
  }
}

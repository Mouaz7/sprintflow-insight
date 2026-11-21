package com.sprintflowinsight.realtime;

import java.util.OptionalLong;
import java.util.regex.Pattern;

/** Canonical project destinations for project notifications, presence and shared text. */
final class ProjectTopics {
  static final String OPEN_TASK = "/app/projects/{projectId}/presence";
  static final String EVENTS = "/topic/projects/{projectId}";
  static final String PRESENCE = "/topic/projects/{projectId}/presence";
  static final String CRDT_UPDATES =
      "/topic/projects/{projectId}/collaboration/{target}/{id}/updates";
  static final String CRDT_AWARENESS =
      "/topic/projects/{projectId}/collaboration/{target}/{id}/awareness";
  static final String CRDT_SEND = "/app/projects/{projectId}/collaboration/{target}/{id}/updates";
  static final String CRDT_CARET =
      "/app/projects/{projectId}/collaboration/{target}/{id}/awareness";
  private static final Pattern EVENTS_TOPIC = Pattern.compile("/topic/projects/([1-9][0-9]{0,17})");
  private static final Pattern CRDT_TOPIC =
      Pattern.compile(
          "/topic/projects/([1-9][0-9]{0,17})/collaboration/(?:TASK|RETROSPECTIVE)/[1-9][0-9]{0,17}/(?:updates|awareness)");

  private ProjectTopics() {}

  static String events(long projectId) {
    return "/topic/projects/" + projectId;
  }

  static String presence(long projectId) {
    return events(projectId) + "/presence";
  }

  static String collaboration(long projectId, String target, long id, String topic) {
    return events(projectId) + "/collaboration/" + target + "/" + id + "/" + topic;
  }

  /** The project of an events topic; presence topics and anything else give empty. */
  static OptionalLong eventsProject(String destination) {
    if (destination == null) return OptionalLong.empty();
    var matcher = EVENTS_TOPIC.matcher(destination);
    return matcher.matches()
        ? OptionalLong.of(Long.parseLong(matcher.group(1)))
        : OptionalLong.empty();
  }

  /** The project of either topic, or empty for any other destination. */
  static OptionalLong project(String destination) {
    if (destination == null) return OptionalLong.empty();
    var collaboration = CRDT_TOPIC.matcher(destination);
    if (collaboration.matches()) return OptionalLong.of(Long.parseLong(collaboration.group(1)));
    if (destination.endsWith("/presence"))
      return eventsProject(destination.substring(0, destination.length() - "/presence".length()));
    return eventsProject(destination);
  }
}

package com.sprintflowinsight.realtime;

import java.util.OptionalLong;
import java.util.regex.Pattern;

/** Destination names for one project. Only these two topics exist for clients. */
final class ProjectTopics {
  static final String EVENTS = "/topic/projects/{projectId}";
  static final String PRESENCE = "/topic/projects/{projectId}/presence";
  private static final Pattern EVENTS_TOPIC = Pattern.compile("/topic/projects/([1-9][0-9]{0,17})");

  private ProjectTopics() {}

  static String events(long projectId) {
    return "/topic/projects/" + projectId;
  }

  static String presence(long projectId) {
    return events(projectId) + "/presence";
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
    if (destination != null && destination.endsWith("/presence"))
      return eventsProject(destination.substring(0, destination.length() - "/presence".length()));
    return eventsProject(destination);
  }
}

package com.sprintflowinsight.realtime;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Who is viewing which project, kept in memory on this instance. A WebSocket session is present in
 * a project while it subscribes to the project's events topic. Entries leave on UNSUBSCRIBE or
 * disconnect, and also when nobody has confirmed the session for {@code ttl}, so a lost disconnect
 * cannot leave a ghost viewer. With several instances each one only knows its own connections; C6
 * moves presence to the broker relay (ADR 0017).
 *
 * <p>Methods return the projects whose viewer list changed so the caller can broadcast them.
 */
final class PresenceRegistry {
  /** One subscription; only events-topic subscriptions make the user a viewer. */
  private record Topic(long projectId, boolean events) {}

  private static final class Connection {
    final long userId;
    final String name;
    final Map<String, Topic> subscriptions = new HashMap<>();
    Instant seen;

    Connection(long userId, String name, Instant seen) {
      this.userId = userId;
      this.name = name;
      this.seen = seen;
    }

    boolean views(long projectId) {
      for (Topic topic : subscriptions.values())
        if (topic.events() && topic.projectId() == projectId) return true;
      return false;
    }
  }

  private final Map<String, Connection> connections = new HashMap<>();
  private final Duration ttl;

  PresenceRegistry(Duration ttl) {
    this.ttl = ttl;
  }

  /**
   * Records a subscription to a project's events topic ({@code events}) or presence topic. Returns
   * the project when its viewer list changed.
   */
  synchronized Set<Long> subscribed(
      String sessionId,
      String subscriptionId,
      long projectId,
      boolean events,
      long userId,
      String name,
      Instant now) {
    Connection connection =
        connections.computeIfAbsent(sessionId, id -> new Connection(userId, name, now));
    connection.seen = now;
    boolean wasViewing = viewing(projectId, userId);
    connection.subscriptions.put(subscriptionId, new Topic(projectId, events));
    return wasViewing || !events ? Set.of() : Set.of(projectId);
  }

  synchronized Set<Long> unsubscribed(String sessionId, String subscriptionId) {
    Connection connection = connections.get(sessionId);
    if (connection == null) return Set.of();
    Topic topic = connection.subscriptions.remove(subscriptionId);
    if (topic == null || !topic.events() || viewing(topic.projectId(), connection.userId))
      return Set.of();
    return Set.of(topic.projectId());
  }

  synchronized Set<Long> disconnected(String sessionId) {
    return removeConnection(sessionId);
  }

  /** Confirms a live session; see {@link #expire}. */
  synchronized void touch(String sessionId, Instant now) {
    Connection connection = connections.get(sessionId);
    if (connection != null) connection.seen = now;
  }

  /** Drops sessions not confirmed within the TTL and returns the projects that changed. */
  synchronized Set<Long> expire(Instant now) {
    Instant cutoff = now.minus(ttl);
    Set<Long> changed = new HashSet<>();
    for (String sessionId : List.copyOf(connections.keySet()))
      if (connections.get(sessionId).seen.isBefore(cutoff))
        changed.addAll(removeConnection(sessionId));
    return changed;
  }

  synchronized Set<Long> projects(String sessionId) {
    Connection connection = connections.get(sessionId);
    if (connection == null) return Set.of();
    Set<Long> projects = new HashSet<>();
    for (Topic topic : connection.subscriptions.values()) projects.add(topic.projectId());
    return projects;
  }

  synchronized int subscriptions(String sessionId) {
    Connection connection = connections.get(sessionId);
    return connection == null ? 0 : connection.subscriptions.size();
  }

  /** Distinct viewers of a project ordered by name, then id. */
  synchronized List<PresenceMessage.Viewer> viewers(long projectId) {
    Map<Long, PresenceMessage.Viewer> byUser = new HashMap<>();
    for (Connection connection : connections.values())
      if (connection.views(projectId))
        byUser.putIfAbsent(
            connection.userId, new PresenceMessage.Viewer(connection.userId, connection.name));
    var sorted =
        new TreeSet<PresenceMessage.Viewer>(
            Comparator.comparing(PresenceMessage.Viewer::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(PresenceMessage.Viewer::userId));
    sorted.addAll(byUser.values());
    return List.copyOf(sorted);
  }

  private Set<Long> removeConnection(String sessionId) {
    Connection connection = connections.remove(sessionId);
    if (connection == null) return Set.of();
    Set<Long> changed = new HashSet<>();
    for (Topic topic : connection.subscriptions.values())
      if (topic.events() && !viewing(topic.projectId(), connection.userId))
        changed.add(topic.projectId());
    return changed;
  }

  private boolean viewing(long projectId, long userId) {
    for (Connection connection : connections.values())
      if (connection.userId == userId && connection.views(projectId)) return true;
    return false;
  }
}

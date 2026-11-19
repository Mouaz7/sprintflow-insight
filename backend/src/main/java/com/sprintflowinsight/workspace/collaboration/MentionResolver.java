package com.sprintflowinsight.workspace.collaboration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Maps parsed handles to project members. Only the given members can ever be returned, so a mention
 * of an outsider resolves to nothing.
 *
 * <ul>
 *   <li>A handle containing {@code @} must equal a member's email (case-insensitive).
 *   <li>Any other handle must equal the local part of exactly one member's email. An ambiguous
 *       local part ({@code anna@a.test} and {@code anna@b.test}) resolves to nobody; the author can
 *       write the full address instead.
 * </ul>
 */
public final class MentionResolver {
  private MentionResolver() {}

  /** A project member as the resolver sees it. */
  public record Candidate(Long userId, String email) {}

  /** Distinct user ids of the mentioned members, in the order the handles were given. */
  public static List<Long> resolve(List<String> handles, Collection<Candidate> members) {
    if (handles.isEmpty() || members.isEmpty()) return List.of();
    Map<String, Long> byEmail = new HashMap<>();
    Map<String, Set<Long>> byLocalPart = new HashMap<>();
    for (Candidate member : members) {
      if (member.userId() == null || member.email() == null) continue;
      String email = member.email().trim().toLowerCase(Locale.ROOT);
      byEmail.put(email, member.userId());
      int at = email.indexOf('@');
      String local = at < 0 ? email : email.substring(0, at);
      byLocalPart.computeIfAbsent(local, key -> new LinkedHashSet<>()).add(member.userId());
    }
    Set<Long> resolved = new LinkedHashSet<>();
    for (String raw : handles) {
      String handle = raw.toLowerCase(Locale.ROOT);
      if (handle.indexOf('@') >= 0) {
        Long id = byEmail.get(handle);
        if (id != null) resolved.add(id);
      } else {
        Set<Long> matches = byLocalPart.get(handle);
        if (matches != null && matches.size() == 1) resolved.add(matches.iterator().next());
      }
    }
    return new ArrayList<>(resolved);
  }
}

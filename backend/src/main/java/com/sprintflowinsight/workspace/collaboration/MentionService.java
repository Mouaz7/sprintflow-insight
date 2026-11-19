package com.sprintflowinsight.workspace.collaboration;

import com.sprintflowinsight.workspace.events.MentionAdded;
import com.sprintflowinsight.workspace.project.ProjectMemberRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns {@code @handle}s in comments into mentions of project members, and publishes {@link
 * MentionAdded} for members who were not mentioned before. Runs inside the caller's write
 * transaction, so the event joins the outbox atomically with the text.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class MentionService {
  private final ProjectMemberRepository members;
  private final CommentMentions mentions;
  private final ApplicationEventPublisher events;

  public MentionService(
      ProjectMemberRepository members, CommentMentions mentions, ApplicationEventPublisher events) {
    this.members = members;
    this.mentions = mentions;
    this.events = events;
  }

  /** Members of {@code projectId} mentioned in {@code text}; no query when there is no handle. */
  public List<Long> mentioned(Long projectId, String text) {
    List<String> handles = MentionParser.parse(text);
    if (handles.isEmpty()) return List.of();
    return MentionResolver.resolve(
        handles,
        members.findByProjectId(projectId).stream()
            .map(member -> new MentionResolver.Candidate(member.getUserId(), member.getEmail()))
            .toList());
  }

  /** Records the mentions of a new or edited comment and notifies newly mentioned members. */
  public void commentSaved(TaskComment comment, String taskTitle, Long actorUserId) {
    List<Long> current = mentioned(comment.getProjectId(), comment.getMessage());
    List<Long> previous = mentions.userIds(comment.getId());
    if (!Set.copyOf(current).equals(Set.copyOf(previous)))
      mentions.replace(comment.getId(), comment.getProjectId(), current, Instant.now());
    publish(
        comment.getProjectId(),
        comment.getTaskId(),
        comment.getId(),
        taskTitle,
        actorUserId,
        added(previous, current, actorUserId));
  }

  static List<Long> added(List<Long> previous, List<Long> current, Long actorUserId) {
    Set<Long> added = new LinkedHashSet<>(current);
    added.removeAll(previous);
    added.remove(actorUserId);
    return new ArrayList<>(added);
  }

  private void publish(
      Long projectId,
      Long taskId,
      Long commentId,
      String title,
      Long actorUserId,
      List<Long> recipients) {
    if (recipients.isEmpty()) return;
    events.publishEvent(
        new MentionAdded(
            UUID.randomUUID(),
            projectId,
            actorUserId,
            Instant.now(),
            taskId,
            commentId,
            title,
            recipients));
  }
}

package com.sprintflowinsight.workspace.collaboration;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Storage for {@code comment_mention}: which members a comment mentions. */
@Component
public class CommentMentions {
  private final JdbcClient jdbc;

  public CommentMentions(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<Long> userIds(Long commentId) {
    return jdbc.sql("select user_id from comment_mention where comment_id = ? order by user_id")
        .param(commentId)
        .query(Long.class)
        .list();
  }

  /** Replaces the comment's mentions with {@code userIds}. */
  public void replace(Long commentId, Long projectId, Collection<Long> userIds, Instant now) {
    jdbc.sql("delete from comment_mention where comment_id = ?").param(commentId).update();
    for (Long userId : userIds)
      jdbc.sql(
              "insert into comment_mention(comment_id, user_id, project_id, created_at)"
                  + " values (?, ?, ?, ?)")
          .param(commentId)
          .param(userId)
          .param(projectId)
          .param(java.sql.Timestamp.from(now))
          .update();
  }
}

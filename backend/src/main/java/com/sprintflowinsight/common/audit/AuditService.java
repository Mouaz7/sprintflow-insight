package com.sprintflowinsight.common.audit;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Service
public class AuditService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public AuditService(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      Long projectId,
      Long actorId,
      String role,
      String action,
      String targetType,
      Object targetId,
      Map<String, ?> details) {
    if (actorId == null
        || !Set.of("Owner", "Admin", "Member", "Account").contains(role)
        || !action.matches("[A-Z_]{1,64}")
        || !targetType.matches("[a-z_]{1,32}"))
      throw new IllegalArgumentException("Invalid audit identity or action.");
    String payload = json.writeValueAsString(details);
    if (payload.length() > 3000) throw new IllegalArgumentException("Audit metadata is too large.");
    jdbc.update(
        "insert into"
            + " admin_audit_event(project_id,actor_user_id,actor_role,action,target_type,target_id,details)"
            + " values(?,?,?,?,?,?,cast(? as jsonb))",
        projectId,
        actorId,
        role,
        action,
        targetType,
        targetId == null ? null : targetId.toString(),
        payload);
  }

  public record Entry(
      Long id,
      Long projectId,
      Long actorUserId,
      String actorRole,
      String action,
      String targetType,
      String targetId,
      Map<String, Object> details,
      Instant createdAt) {}

  public record Page(List<Entry> items, Long nextBefore) {}

  public Page page(Long projectId, Long before, int size) {
    if (size < 1 || size > 100 || before != null && before < 1)
      throw new IllegalArgumentException("Invalid audit page.");
    var rows =
        jdbc.query(
            "select"
                + " id,project_id,actor_user_id,actor_role,action,target_type,target_id,details::text,created_at"
                + " from admin_audit_event where project_id=? and (?::bigint is null or id<?) order"
                + " by id desc limit ?",
            (r, n) ->
                new Entry(
                    r.getLong(1),
                    r.getLong(2),
                    r.getLong(3),
                    r.getString(4),
                    r.getString(5),
                    r.getString(6),
                    r.getString(7),
                    json.readValue(
                        r.getString(8),
                        new tools.jackson.core.type.TypeReference<Map<String, Object>>() {}),
                    r.getTimestamp(9).toInstant()),
            projectId,
            before,
            before,
            size + 1);
    boolean more = rows.size() > size;
    var items = rows.stream().limit(size).toList();
    return new Page(items, more ? items.getLast().id() : null);
  }
}

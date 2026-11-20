package com.sprintflowinsight.workspace.time;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {
  /** The account's running timer in any project; the unique index allows at most one. */
  @Query("select e from TimeEntry e where e.userId = :userId and e.minutes is null")
  Optional<TimeEntry> findRunning(@Param("userId") Long userId);

  /**
   * Newest first with keyset paging on the id. The member's display email and the task's key are
   * read in the same query; the email is {@code null} for former members and deleted accounts.
   */
  @Query(
      """
      select new com.sprintflowinsight.workspace.time.TimeEntryRow(
        e, m.email, t.title, t.projectKey, t.number)
      from TimeEntry e
      join com.sprintflowinsight.workspace.task.Task t on t.id = e.taskId
      left join com.sprintflowinsight.workspace.project.ProjectMember m
        on m.projectId = e.projectId and m.userId = e.userId
      where e.projectId = :projectId and t.trashedAt is null
        and (:taskId is null or e.taskId = :taskId)
        and (:sprintId is null or t.sprintId = :sprintId)
        and (:userId is null or e.userId = :userId)
        and (:before is null or e.id < :before)
      order by e.id desc
      """)
  List<TimeEntryRow> page(
      @Param("projectId") Long projectId,
      @Param("taskId") Long taskId,
      @Param("sprintId") Long sprintId,
      @Param("userId") Long userId,
      @Param("before") Long before,
      Limit limit);

  /** One entry with its display fields, for responses after a change. */
  @Query(
      """
      select new com.sprintflowinsight.workspace.time.TimeEntryRow(
        e, m.email, t.title, t.projectKey, t.number)
      from TimeEntry e
      join com.sprintflowinsight.workspace.task.Task t on t.id = e.taskId
      left join com.sprintflowinsight.workspace.project.ProjectMember m
        on m.projectId = e.projectId and m.userId = e.userId
      where e.id = :id
      """)
  Optional<TimeEntryRow> row(@Param("id") Long id);

  /**
   * Logged minutes per live task in the project, or in one sprint when {@code sprintId} is set.
   * Running timers count only once stopped. Rows are {@code [taskId, minutes]}.
   */
  @Query(
      """
      select e.taskId, sum(e.minutes) from TimeEntry e
      join com.sprintflowinsight.workspace.task.Task t on t.id = e.taskId
      where t.projectId = :projectId and (:sprintId is null or t.sprintId = :sprintId)
        and t.trashedAt is null and e.minutes is not null
      group by e.taskId
      """)
  List<Object[]> minutesByTask(
      @Param("projectId") Long projectId, @Param("sprintId") Long sprintId);

  /** Logged minutes per account on the sprint's live tasks. Rows are {@code [userId, minutes]}. */
  @Query(
      """
      select e.userId, sum(e.minutes) from TimeEntry e
      join com.sprintflowinsight.workspace.task.Task t on t.id = e.taskId
      where t.sprintId = :sprintId and t.projectId = :projectId and t.trashedAt is null
        and e.minutes is not null
      group by e.userId
      """)
  List<Object[]> minutesByMemberInSprint(
      @Param("projectId") Long projectId, @Param("sprintId") Long sprintId);

  @Modifying
  @Query("delete from TimeEntry e where e.userId = :userId and e.minutes is null")
  int deleteRunningFor(@Param("userId") Long userId);

  /** Keeps a deleted account's hours with the project but removes the link to the person. */
  @Modifying
  @Query(
      "update TimeEntry e set e.userId = null, e.version = e.version + 1 where e.userId = :userId")
  int anonymize(@Param("userId") Long userId);
}

package com.sprintflowinsight.workspace.time;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SprintCapacityRepository extends JpaRepository<SprintCapacity, Long> {
  List<SprintCapacity> findBySprintId(Long sprintId);

  Optional<SprintCapacity> findBySprintIdAndUserId(Long sprintId, Long userId);

  /**
   * Committed work in the sprint per assignee, in one grouped query: estimate minutes, story points
   * and the number of tasks without an estimate. Done tasks count, because the sprint committed to
   * them. Rows are {@code [lower(email) or null, minutes, points, unestimated]}.
   */
  @Query(
      """
      select lower(t.assigneeEmail), sum(coalesce(t.estimateMinutes, 0)), sum(t.storyPoints),
        sum(case when t.estimateMinutes is null then 1 else 0 end)
      from com.sprintflowinsight.workspace.task.Task t
      where t.projectId = :projectId and t.sprintId = :sprintId and t.trashedAt is null
      group by lower(t.assigneeEmail)
      """)
  List<Object[]> committedByAssignee(
      @Param("projectId") Long projectId, @Param("sprintId") Long sprintId);

  @Modifying
  @Query("delete from SprintCapacity c where c.userId = :userId")
  int deleteForUser(@Param("userId") Long userId);
}

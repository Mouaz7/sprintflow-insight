package com.sprintflowinsight.repository;

import com.sprintflowinsight.model.Task;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
  @Query(
      """
      select task from Task task where task.trashedAt is null
      and task.dueAt < :tomorrow and (task.status is null or task.status <> 'Done')
      and exists (select project.id from Project project where project.id = task.projectId
        and project.trashedAt is null and project.archivedAt is null)
      order by task.id
      """)
  org.springframework.data.domain.Slice<Task> findDueForReminder(
      @Param("tomorrow") java.time.Instant tomorrow,
      org.springframework.data.domain.Pageable pageable);

  @org.springframework.data.jpa.repository.Query(
      "select item from Task item where item.projectId = :projectId and item.trashedAt is null")
  List<Task> findByProjectId(
      @org.springframework.data.repository.query.Param("projectId") Long projectId);

  @org.springframework.data.jpa.repository.Query(
      "select item from Task item where item.sprintId = :sprintId and item.trashedAt is null")
  List<Task> findBySprintId(
      @org.springframework.data.repository.query.Param("sprintId") Long sprintId);

  List<Task> findAllByProjectIdAndStoryId(Long projectId, Long storyId);

  List<Task> findAllByProjectIdAndAssigneeEmailIgnoreCase(Long projectId, String email);

  List<Task> findByAssigneeEmailIgnoreCase(String assigneeEmail);

  @Modifying
  @Query("update Task t set t.storyId = null, t.version = t.version + 1 where t.storyId = :storyId")
  void clearStoryReference(@Param("storyId") Long storyId);

  // ---- Set-based lifecycle updates. Each bumps the optimistic-lock version, so clients holding
  // an older copy still get 412 instead of silently overwriting the change.

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update Task t set t.trashedAt = :now, t.deletionBatchId = :batch,
        t.version = coalesce(t.version, 0) + 1
      where t.projectId = :projectId and t.storyId = :storyId and t.trashedAt is null
      """)
  int trashWithStory(
      @Param("projectId") Long projectId,
      @Param("storyId") Long storyId,
      @Param("now") java.time.Instant now,
      @Param("batch") String batch);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update Task t set t.trashedAt = :now, t.deletionBatchId = :batch,
        t.version = coalesce(t.version, 0) + 1
      where t.projectId = :projectId and t.trashedAt is null and (t.sprintId = :sprintId
        or t.storyId in (select s.id from UserStory s
          where s.projectId = :projectId and s.sprintId = :sprintId))
      """)
  int trashWithSprint(
      @Param("projectId") Long projectId,
      @Param("sprintId") Long sprintId,
      @Param("now") java.time.Instant now,
      @Param("batch") String batch);

  /** Restores everything trashed by one operation (all children share its batch id). */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update Task t set t.trashedAt = null, t.deletionBatchId = null,
        t.version = coalesce(t.version, 0) + 1
      where t.projectId = :projectId and t.deletionBatchId = :batch and t.trashedAt is not null
      """)
  int restoreBatch(@Param("projectId") Long projectId, @Param("batch") String batch);

  /** Trash created before batch ids existed is matched by its exact timestamp. */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update Task t set t.trashedAt = null, t.deletionBatchId = null,
        t.version = coalesce(t.version, 0) + 1
      where t.projectId = :projectId and t.trashedAt = :trashedAt and (t.storyId = :storyId
        or t.sprintId = :sprintId or t.storyId in (select s.id from UserStory s
          where s.projectId = :projectId and s.sprintId = :sprintId))
      """)
  int restoreLegacy(
      @Param("projectId") Long projectId,
      @Param("storyId") Long storyId,
      @Param("sprintId") Long sprintId,
      @Param("trashedAt") java.time.Instant trashedAt);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update Task t set t.sprintId = null, t.version = coalesce(t.version, 0) + 1
      where t.projectId = :projectId and t.sprintId = :sprintId
      """)
  int detachFromSprint(@Param("projectId") Long projectId, @Param("sprintId") Long sprintId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update Task t set t.assigneeEmail = null, t.version = coalesce(t.version, 0) + 1
      where t.projectId = :projectId and lower(t.assigneeEmail) = lower(:email)
      """)
  int unassignInProject(@Param("projectId") Long projectId, @Param("email") String email);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      """
      update Task t set t.assigneeEmail = null, t.version = coalesce(t.version, 0) + 1
      where lower(t.assigneeEmail) = lower(:email)
      """)
  int unassignEverywhere(@Param("email") String email);

  void deleteByProjectId(Long projectId);

  java.util.List<Task> findAllByProjectId(Long projectId);

  java.util.List<Task> findByProjectIdInAndTrashedAtIsNotNull(java.util.List<Long> projectIds);
}

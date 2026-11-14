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

  void deleteByProjectId(Long projectId);

  java.util.List<Task> findAllByProjectId(Long projectId);

  java.util.List<Task> findByProjectIdInAndTrashedAtIsNotNull(java.util.List<Long> projectIds);
}

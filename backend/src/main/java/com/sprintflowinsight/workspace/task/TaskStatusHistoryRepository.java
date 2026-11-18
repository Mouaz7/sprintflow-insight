package com.sprintflowinsight.workspace.task;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskStatusHistoryRepository extends JpaRepository<TaskStatusHistory, Long> {
  List<TaskStatusHistory> findByProjectIdOrderByChangedAtDesc(Long projectId);

  List<TaskStatusHistory> findByTaskIdOrderByChangedAtDesc(Long taskId);

  /** The project's history in persisted order; served by the (project_id, changed_at) index. */
  List<TaskStatusHistory> findByProjectIdOrderByChangedAtAscIdAsc(Long projectId);

  void deleteByTaskId(Long taskId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update TaskStatusHistory event set event.actorEmail = 'Former member' where"
          + " lower(event.actorEmail) = lower(:email)")
  int anonymizeActorEmail(@Param("email") String email);

  void deleteByProjectId(Long projectId);
}

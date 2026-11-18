package com.sprintflowinsight.workspace.roadmap;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface TaskDependencyRepository extends JpaRepository<TaskDependency, Long> {
  List<TaskDependency> findByProjectIdOrderByIdAsc(Long projectId);

  Optional<TaskDependency> findByIdAndProjectId(Long id, Long projectId);

  boolean existsByBlockerTaskIdAndBlockedTaskId(Long blockerTaskId, Long blockedTaskId);

  long countByProjectId(Long projectId);
}

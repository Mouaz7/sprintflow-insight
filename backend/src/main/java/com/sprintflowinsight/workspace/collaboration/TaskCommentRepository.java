package com.sprintflowinsight.workspace.collaboration;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {
  List<TaskComment> findByTaskIdOrderByCreatedAtAsc(Long taskId);

  void deleteByTaskId(Long taskId);

  void deleteByAuthorEmail(String authorEmail);

  void deleteByProjectId(Long projectId);
}

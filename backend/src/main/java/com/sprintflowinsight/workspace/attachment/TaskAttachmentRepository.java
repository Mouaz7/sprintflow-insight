package com.sprintflowinsight.workspace.attachment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {
  List<TaskAttachment> findByTaskIdOrderByCreatedAtAsc(Long taskId);

  void deleteByTaskId(Long taskId);

  List<TaskAttachment> findByProjectId(Long projectId);

  void deleteByProjectId(Long projectId);
}

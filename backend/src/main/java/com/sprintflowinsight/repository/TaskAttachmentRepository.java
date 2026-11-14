package com.sprintflowinsight.repository;
import com.sprintflowinsight.model.TaskAttachment; import org.springframework.data.jpa.repository.JpaRepository; import java.util.List;
public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment,Long>{List<TaskAttachment> findByTaskIdOrderByCreatedAtAsc(Long taskId); void deleteByTaskId(Long taskId);
    List<TaskAttachment> findByProjectId(Long projectId);
    void deleteByProjectId(Long projectId);
}

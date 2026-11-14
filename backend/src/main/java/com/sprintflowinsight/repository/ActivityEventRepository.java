package com.sprintflowinsight.repository;

import com.sprintflowinsight.model.ActivityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, Long> {
    List<ActivityEvent> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    void deleteByActorEmail(String actorEmail);
    void deleteByProjectId(Long projectId);
}

package com.sprintflowinsight.workspace.activity;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, Long> {
  List<ActivityEvent> findByProjectIdOrderByCreatedAtDesc(Long projectId);

  void deleteByActorEmail(String actorEmail);

  void deleteByProjectId(Long projectId);
}

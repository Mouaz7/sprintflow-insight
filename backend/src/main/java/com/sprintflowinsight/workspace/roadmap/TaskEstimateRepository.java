package com.sprintflowinsight.workspace.roadmap;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface TaskEstimateRepository extends JpaRepository<TaskEstimate, Long> {
  List<TaskEstimate> findByProjectId(Long projectId);
}

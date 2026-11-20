package com.sprintflowinsight.workspace.dashboard;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardRepository extends JpaRepository<DashboardLayout, Long> {
  Optional<DashboardLayout> findByProjectIdAndUserId(Long projectId, Long userId);
}

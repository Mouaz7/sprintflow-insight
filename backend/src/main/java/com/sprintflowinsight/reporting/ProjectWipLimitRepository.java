package com.sprintflowinsight.reporting;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProjectWipLimitRepository extends JpaRepository<ProjectWipLimit, ProjectWipLimit.Key> {
  List<ProjectWipLimit> findByProjectId(Long projectId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from ProjectWipLimit l where l.projectId = :projectId")
  int deleteByProjectId(@Param("projectId") Long projectId);
}

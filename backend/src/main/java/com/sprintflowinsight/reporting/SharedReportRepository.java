package com.sprintflowinsight.reporting;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedReportRepository extends JpaRepository<SharedReport, Long> {
  Optional<SharedReport> findByTokenHash(String tokenHash);

  List<SharedReport> findByProjectIdOrderByCreatedAtDesc(Long projectId);

  void deleteByProjectId(Long projectId);

  void deleteByCreatedBy(String createdBy);
}

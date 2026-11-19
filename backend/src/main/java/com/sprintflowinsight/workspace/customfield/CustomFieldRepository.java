package com.sprintflowinsight.workspace.customfield;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface CustomFieldRepository extends JpaRepository<CustomField, Long> {
  List<CustomField> findByProjectIdOrderByIdAsc(Long projectId);

  Optional<CustomField> findByIdAndProjectId(Long id, Long projectId);

  long countByProjectId(Long projectId);
}

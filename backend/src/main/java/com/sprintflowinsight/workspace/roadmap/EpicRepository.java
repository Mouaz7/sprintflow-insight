package com.sprintflowinsight.workspace.roadmap;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface EpicRepository extends JpaRepository<Epic, Long> {
  List<Epic> findByProjectIdOrderByIdAsc(Long projectId);

  Optional<Epic> findByIdAndProjectId(Long id, Long projectId);

  long countByProjectId(Long projectId);
}

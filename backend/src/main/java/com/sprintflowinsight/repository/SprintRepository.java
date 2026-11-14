package com.sprintflowinsight.repository;

import com.sprintflowinsight.model.Sprint;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SprintRepository extends JpaRepository<Sprint, Long> {
  @org.springframework.data.jpa.repository.Query(
      "select item from Sprint item where item.projectId = :projectId and item.trashedAt is null")
  List<Sprint> findByProjectId(
      @org.springframework.data.repository.query.Param("projectId") Long projectId);

  void deleteByProjectId(Long projectId);

  java.util.List<Sprint> findAllByProjectId(Long projectId);

  java.util.List<Sprint> findByProjectIdInAndTrashedAtIsNotNull(java.util.List<Long> projectIds);
}

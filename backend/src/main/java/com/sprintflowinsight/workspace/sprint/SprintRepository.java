package com.sprintflowinsight.workspace.sprint;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SprintRepository extends JpaRepository<Sprint, Long> {
  @org.springframework.data.jpa.repository.Query(
      "select item from Sprint item where item.projectId = :projectId and item.trashedAt is null")
  List<Sprint> findByProjectId(
      @org.springframework.data.repository.query.Param("projectId") Long projectId);

  /** Active sprints in every live project the user owns or belongs to (single query). */
  @org.springframework.data.jpa.repository.Query(
      """
      select sprint from Sprint sprint, com.sprintflowinsight.workspace.project.Project project
      where sprint.projectId = project.id
        and sprint.trashedAt is null and project.trashedAt is null
        and exists (
          select member.id from com.sprintflowinsight.workspace.project.ProjectMember member
          where member.projectId = project.id and member.userId = :userId)
      order by sprint.id
      """)
  List<Sprint> findVisibleTo(
      @org.springframework.data.repository.query.Param("userId") Long userId);

  void deleteByProjectId(Long projectId);

  java.util.List<Sprint> findAllByProjectId(Long projectId);

  java.util.List<Sprint> findByProjectIdInAndTrashedAtIsNotNull(java.util.List<Long> projectIds);
}

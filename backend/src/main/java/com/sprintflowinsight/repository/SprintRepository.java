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

  /** Active sprints in every live project the user owns or belongs to (single query). */
  @org.springframework.data.jpa.repository.Query(
      """
      select sprint from Sprint sprint, com.sprintflowinsight.project.Project project
      where sprint.projectId = project.id
        and sprint.trashedAt is null and project.trashedAt is null
        and (lower(project.ownerEmail) = lower(:email) or exists (
          select member.id from com.sprintflowinsight.project.ProjectMember member
          where member.projectId = project.id and lower(member.email) = lower(:email)))
      order by sprint.id
      """)
  List<Sprint> findVisibleTo(@org.springframework.data.repository.query.Param("email") String email);

  void deleteByProjectId(Long projectId);

  java.util.List<Sprint> findAllByProjectId(Long projectId);

  java.util.List<Sprint> findByProjectIdInAndTrashedAtIsNotNull(java.util.List<Long> projectIds);
}

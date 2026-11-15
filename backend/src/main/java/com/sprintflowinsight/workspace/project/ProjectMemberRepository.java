package com.sprintflowinsight.workspace.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {
  List<ProjectMember> findByProjectId(Long projectId);

  List<ProjectMember> findByProjectIdIn(java.util.Collection<Long> projectIds);

  List<ProjectMember> findByEmailIgnoreCase(String email);

  Optional<ProjectMember> findByProjectIdAndEmailIgnoreCase(Long projectId, String email);

  void deleteByProjectIdAndEmailIgnoreCase(Long projectId, String email);

  void deleteByEmailIgnoreCase(String email);

  void deleteByProjectId(Long projectId);
}

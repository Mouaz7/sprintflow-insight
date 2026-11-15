package com.sprintflowinsight.workspace.collaboration;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetrospectiveRepository extends JpaRepository<RetrospectiveEntry, Long> {
  List<RetrospectiveEntry> findByProjectIdAndSprintIdOrderByCreatedAtAsc(
      Long projectId, Long sprintId);

  void deleteByProjectId(Long projectId);

  void deleteBySprintId(Long sprintId);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "update RetrospectiveEntry entry set entry.authorEmail = 'Former member' where"
          + " lower(entry.authorEmail) = lower(:email)")
  void anonymizeAuthor(@org.springframework.data.repository.query.Param("email") String email);
}

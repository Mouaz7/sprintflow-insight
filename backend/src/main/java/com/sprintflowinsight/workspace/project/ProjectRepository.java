package com.sprintflowinsight.workspace.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
  @org.springframework.data.jpa.repository.Query(
      """
      select project from Project project
      where project.trashedAt is null and exists (
          select member.id from ProjectMember member
          where member.projectId = project.id and member.userId = :userId)
      order by project.id
      """)
  List<Project> findVisibleTo(
      @org.springframework.data.repository.query.Param("userId") Long userId,
      org.springframework.data.domain.Pageable pageable);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select project from Project project where project.id = :id")
  Optional<Project> lockById(@org.springframework.data.repository.query.Param("id") Long id);

  @org.springframework.data.jpa.repository.Query(
      "select p from Project p where exists (select m.id from ProjectMember m where"
          + " m.projectId=p.id and m.userId=:userId) order by p.id")
  List<Project> findAccessibleIncludingTrash(
      @org.springframework.data.repository.query.Param("userId") Long userId);

  /**
   * Project state plus the caller's membership role, in one round trip. Membership is matched by
   * the immutable account id, so an email change can never grant or lose access.
   */
  @org.springframework.data.jpa.repository.Query(
      """
      select new com.sprintflowinsight.workspace.project.ProjectRepository$AccessRow(
        project.ownerEmail, project.archivedAt, project.trashedAt, member.role)
      from Project project
      left join ProjectMember member
        on member.projectId = project.id and member.userId = :userId
      where project.id = :id
      """)
  Optional<AccessRow> findAccessRow(
      @org.springframework.data.repository.query.Param("id") Long id,
      @org.springframework.data.repository.query.Param("userId") Long userId);

  record AccessRow(
      String ownerEmail,
      java.time.Instant archivedAt,
      java.time.Instant trashedAt,
      String memberRole) {}

  Optional<Project> findByName(String name);

  Optional<Project> findByProjectKey(String key);

  boolean existsByProjectKey(String key);

  /** Keys beginning with {@code prefix}, to pick a free derived key in one round trip. */
  @org.springframework.data.jpa.repository.Query(
      value = "select key from projects where key like :prefix || '%'",
      nativeQuery = true)
  List<String> findKeysStartingWith(
      @org.springframework.data.repository.query.Param("prefix") String prefix);

  List<Project> findByOwnerEmail(String ownerEmail);
}

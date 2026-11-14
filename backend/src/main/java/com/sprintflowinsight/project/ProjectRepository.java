package com.sprintflowinsight.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
  @org.springframework.data.jpa.repository.Query(
      """
      select project from Project project
      where project.trashedAt is null and
        (lower(project.ownerEmail) = lower(:email) or exists (
          select member.id from ProjectMember member
          where member.projectId = project.id and lower(member.email) = lower(:email)))
      order by project.id
      """)
  List<Project> findVisibleTo(
      @org.springframework.data.repository.query.Param("email") String email,
      org.springframework.data.domain.Pageable pageable);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select project from Project project where project.id = :id")
  Optional<Project> lockById(@org.springframework.data.repository.query.Param("id") Long id);

  @org.springframework.data.jpa.repository.Query(
      "select p from Project p where lower(p.ownerEmail)=lower(:email) or exists (select m.id from"
          + " ProjectMember m where m.projectId=p.id and lower(m.email)=lower(:email))")
  List<Project> findAccessibleIncludingTrash(
      @org.springframework.data.repository.query.Param("email") String email);

  Optional<Project> findByName(String name);

  List<Project> findByOwnerEmail(String ownerEmail);
}

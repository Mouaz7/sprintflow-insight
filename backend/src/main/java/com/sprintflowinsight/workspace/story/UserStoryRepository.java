package com.sprintflowinsight.workspace.story;


import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserStoryRepository extends JpaRepository<UserStory, Long> {
  @org.springframework.data.jpa.repository.Query(
      "select item from UserStory item where item.projectId = :projectId and item.trashedAt is"
          + " null order by item.backlogPosition, item.id")
  List<UserStory> findByProjectId(
      @org.springframework.data.repository.query.Param("projectId") Long projectId);

  @org.springframework.data.jpa.repository.Query(
      "select coalesce(max(item.backlogPosition), -1) from UserStory item where item.projectId ="
          + " :projectId")
  long lastPosition(@org.springframework.data.repository.query.Param("projectId") Long projectId);

  @org.springframework.data.jpa.repository.Modifying(
      flushAutomatically = true,
      clearAutomatically = true)
  @org.springframework.data.jpa.repository.Query(
      """
      update UserStory s set s.trashedAt = :now, s.deletionBatchId = :batch,
        s.version = coalesce(s.version, 0) + 1
      where s.projectId = :projectId and s.sprintId = :sprintId and s.trashedAt is null
      """)
  int trashWithSprint(
      @org.springframework.data.repository.query.Param("projectId") Long projectId,
      @org.springframework.data.repository.query.Param("sprintId") Long sprintId,
      @org.springframework.data.repository.query.Param("now") java.time.Instant now,
      @org.springframework.data.repository.query.Param("batch") String batch);

  @org.springframework.data.jpa.repository.Modifying(
      flushAutomatically = true,
      clearAutomatically = true)
  @org.springframework.data.jpa.repository.Query(
      """
      update UserStory s set s.trashedAt = null, s.deletionBatchId = null,
        s.version = coalesce(s.version, 0) + 1
      where s.projectId = :projectId and s.deletionBatchId = :batch and s.trashedAt is not null
      """)
  int restoreBatch(
      @org.springframework.data.repository.query.Param("projectId") Long projectId,
      @org.springframework.data.repository.query.Param("batch") String batch);

  @org.springframework.data.jpa.repository.Modifying(
      flushAutomatically = true,
      clearAutomatically = true)
  @org.springframework.data.jpa.repository.Query(
      """
      update UserStory s set s.trashedAt = null, s.deletionBatchId = null,
        s.version = coalesce(s.version, 0) + 1
      where s.projectId = :projectId and s.sprintId = :sprintId and s.trashedAt = :trashedAt
      """)
  int restoreLegacyForSprint(
      @org.springframework.data.repository.query.Param("projectId") Long projectId,
      @org.springframework.data.repository.query.Param("sprintId") Long sprintId,
      @org.springframework.data.repository.query.Param("trashedAt") java.time.Instant trashedAt);

  @org.springframework.data.jpa.repository.Modifying(
      flushAutomatically = true,
      clearAutomatically = true)
  @org.springframework.data.jpa.repository.Query(
      """
      update UserStory s set s.sprintId = null, s.version = coalesce(s.version, 0) + 1
      where s.projectId = :projectId and s.sprintId = :sprintId
      """)
  int detachFromSprint(
      @org.springframework.data.repository.query.Param("projectId") Long projectId,
      @org.springframework.data.repository.query.Param("sprintId") Long sprintId);

  void deleteByProjectId(Long projectId);

  java.util.List<UserStory> findAllByProjectId(Long projectId);

  java.util.List<UserStory> findByProjectIdInAndTrashedAtIsNotNull(java.util.List<Long> projectIds);
}

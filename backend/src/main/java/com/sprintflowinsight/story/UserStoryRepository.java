package com.sprintflowinsight.story;

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

  void deleteByProjectId(Long projectId);

  java.util.List<UserStory> findAllByProjectId(Long projectId);

  java.util.List<UserStory> findByProjectIdInAndTrashedAtIsNotNull(java.util.List<Long> projectIds);
}

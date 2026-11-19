package com.sprintflowinsight.workspace.poker;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PokerSessionRepository extends JpaRepository<PokerSession, Long> {
  /**
   * Row lock for every state change and vote. Votes, reveal, revote, accept and cancel of one
   * session are therefore serialised: a vote either commits before the reveal reads the round or
   * sees the revealed status and is rejected.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from PokerSession s where s.id = :id")
  Optional<PokerSession> findLockedById(@Param("id") Long id);

  boolean existsByStoryIdAndStatusIn(Long storyId, Collection<PokerSession.Status> statuses);

  @Query(
      """
      select new com.sprintflowinsight.workspace.poker.PokerSessionSummary(
        s.id, s.storyId, story.title, s.status, s.round, s.finalStoryPoints)
      from PokerSession s join UserStory story on story.id = s.storyId
      where s.projectId = :projectId and s.status in :statuses
        and story.trashedAt is null
      order by s.updatedAt desc, s.id desc
      """)
  List<PokerSessionSummary> findVisible(
      @Param("projectId") Long projectId,
      @Param("statuses") Collection<PokerSession.Status> statuses,
      org.springframework.data.domain.Limit limit);

  long countByProjectIdAndStatusIn(Long projectId, Collection<PokerSession.Status> statuses);
}

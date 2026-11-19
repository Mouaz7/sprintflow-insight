package com.sprintflowinsight.notifications.digest;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface DigestRepository extends JpaRepository<DigestSubscription, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from DigestSubscription d where d.userId=:id")
  Optional<DigestSubscription> lock(@Param("id") Long id);

  @Query(
      "select d.userId from DigestSubscription d where d.nextDueAt<=:now order by"
          + " d.nextDueAt,d.userId")
  List<Long> due(@Param("now") Instant now, Pageable page);
}

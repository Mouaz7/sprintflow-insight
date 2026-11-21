package com.sprintflowinsight.common;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class EventPublicationClaims {
  static final int BATCH_SIZE = 100;
  private final JdbcTemplate jdbc;

  EventPublicationClaims(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public Set<UUID> claim(Instant before, Instant notBefore) {
    Timestamp earliest = notBefore == null ? null : Timestamp.from(notBefore);
    return Set.copyOf(
        jdbc.queryForList(
            """
            with eligible as (
              select id from event_publication
              where completion_date is null and publication_date<?
                and (?::timestamptz is null or publication_date>=?)
                and (retry_claim_until is null or retry_claim_until<=clock_timestamp())
              order by publication_date,id limit ? for update skip locked
            )
            update event_publication p set retry_claim_until=clock_timestamp()+interval '2 minutes'
              from eligible where p.id=eligible.id returning p.id
            """,
            UUID.class,
            Timestamp.from(before),
            earliest,
            earliest,
            BATCH_SIZE));
  }
}

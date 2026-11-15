-- B1: per-project sequence numbers and a short replay log for realtime messages (ADR 0017).
-- V16 is reserved for B9 search; Flyway accepts the gap.

-- Highest sequence number handed out per project. The upsert that increments it takes a row lock,
-- so allocations for one project are serialized and commit in sequence order.
CREATE TABLE realtime_sequence (
 project_id BIGINT PRIMARY KEY REFERENCES projects(id) ON DELETE CASCADE,
 last_seq BIGINT NOT NULL CHECK (last_seq > 0)
);

-- Metadata only (type, ids, version); never titles, comments or user data. Clients fetch the
-- missed range after a reconnect. Rows are pruned after app.realtime.retention (default 7 days)
-- and beyond app.realtime.max-events-per-project (default 1000) per project.
CREATE TABLE realtime_event (
 project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
 seq BIGINT NOT NULL,
 source_event_id UUID NOT NULL,
 type VARCHAR(40) NOT NULL,
 entity_id BIGINT,
 entity_version BIGINT,
 occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
 PRIMARY KEY (project_id, seq)
);
-- At-least-once delivery: a replayed module event must not get a second sequence number.
CREATE UNIQUE INDEX realtime_event_source_idx ON realtime_event(source_event_id);
CREATE INDEX realtime_event_occurred_idx ON realtime_event(occurred_at);

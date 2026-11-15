-- Spring Modulith 1.4 JDBC event publication registry; joins the domain transaction.
CREATE TABLE event_publication (
 id UUID PRIMARY KEY,
 listener_id TEXT NOT NULL,
 event_type TEXT NOT NULL,
 serialized_event TEXT NOT NULL,
 publication_date TIMESTAMP WITH TIME ZONE NOT NULL,
 completion_date TIMESTAMP WITH TIME ZONE
);
CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash(serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication(completion_date);
-- A listener may be retried after a crash between its commit and registry completion.
ALTER TABLE notification ADD COLUMN source_event_id UUID;
CREATE UNIQUE INDEX notification_source_recipient_idx ON notification(source_event_id,recipient_email) WHERE source_event_id IS NOT NULL;

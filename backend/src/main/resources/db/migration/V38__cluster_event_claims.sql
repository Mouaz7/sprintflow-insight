-- Additive expansion. Older application instances ignore the claim lease column.
ALTER TABLE event_publication ADD COLUMN retry_claim_until timestamptz;
CREATE INDEX event_publication_retry_claim_idx
    ON event_publication(publication_date, id) WHERE completion_date IS NULL;

-- Presence is a lease owned by the replica serving this WebSocket connection.
CREATE TABLE realtime_presence (
    socket_id text NOT NULL,
    project_id bigint NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    display_name text NOT NULL,
    task_id bigint,
    expires_at timestamptz NOT NULL,
    PRIMARY KEY (socket_id, project_id)
);
CREATE INDEX realtime_presence_project_idx ON realtime_presence(project_id);
CREATE INDEX realtime_presence_expiry_idx ON realtime_presence(expires_at);
CREATE INDEX realtime_presence_user_idx ON realtime_presence(user_id);

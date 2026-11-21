ALTER TABLE automation_github
    ADD COLUMN review_on_open boolean NOT NULL DEFAULT false,
    ADD COLUMN done_on_merge boolean NOT NULL DEFAULT false;

CREATE TABLE github_delivery (
    project_id bigint NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    delivery_id uuid NOT NULL,
    repository varchar(180) NOT NULL,
    body_hash bytea NOT NULL CHECK (octet_length(body_hash) = 32),
    event_type varchar(20) NOT NULL CHECK (event_type IN ('pull_request','push')),
    event_action varchar(15),
    linked_tasks integer NOT NULL DEFAULT 0 CHECK (linked_tasks >= 0),
    moved_tasks integer NOT NULL DEFAULT 0 CHECK (moved_tasks >= 0),
    received_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (project_id, delivery_id),
    UNIQUE (project_id, body_hash)
);
CREATE INDEX github_delivery_history_idx ON github_delivery(project_id, received_at DESC, delivery_id);

CREATE TABLE task_link (
    task_id bigint NOT NULL REFERENCES task(id) ON DELETE CASCADE,
    repository varchar(180) NOT NULL,
    kind varchar(14) NOT NULL CHECK (kind IN ('PULL_REQUEST','COMMIT')),
    remote_key varchar(64) NOT NULL,
    title varchar(250) NOT NULL,
    state varchar(8),
    linked_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (task_id, repository, kind, remote_key),
    CHECK (kind='PULL_REQUEST' AND state IS NOT NULL AND state IN ('OPEN','CLOSED','MERGED')
           OR kind='COMMIT' AND state IS NULL)
);
CREATE INDEX task_link_latest_idx ON task_link(task_id, linked_at DESC, repository, kind, remote_key);

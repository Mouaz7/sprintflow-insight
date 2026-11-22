CREATE TABLE offline_operation (
    user_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    project_id bigint NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    operation_id uuid NOT NULL,
    fingerprint bytea NOT NULL,
    task_version bigint NOT NULL,
    PRIMARY KEY (user_id, project_id, operation_id)
);
CREATE INDEX offline_operation_project_idx ON offline_operation(project_id);

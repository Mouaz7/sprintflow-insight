-- B5: flow analytics.

-- Flow queries read a project's whole status history in time order.
CREATE INDEX history_project_time_idx ON task_status_history (project_id, changed_at);

-- WIP limits per board column. Done never has a limit. Rows go with their project.
CREATE TABLE project_wip_limit (
  project_id BIGINT NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
  status VARCHAR(32) NOT NULL CHECK (status IN ('To Do', 'In Progress', 'Review')),
  wip_limit INTEGER NOT NULL CHECK (wip_limit BETWEEN 1 AND 999),
  PRIMARY KEY (project_id, status)
);

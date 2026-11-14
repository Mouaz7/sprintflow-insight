ALTER TABLE task ADD COLUMN assignee_email VARCHAR(255);
CREATE INDEX task_project_assignee_idx ON task(project_id, assignee_email);

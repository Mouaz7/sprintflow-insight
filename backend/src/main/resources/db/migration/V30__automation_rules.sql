CREATE TABLE automation_rule (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  project_id bigint NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  name varchar(80) NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 80),
  source text NOT NULL CHECK (length(source) BETWEEN 1 AND 4096),
  trigger_kind varchar(24) NOT NULL CHECK (trigger_kind IN ('TASK_MOVED','PULL_REQUEST_MERGED','OVERDUE')),
  enabled boolean NOT NULL,
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX automation_rule_project_name_idx ON automation_rule(project_id, lower(name));
CREATE INDEX automation_rule_trigger_idx ON automation_rule(project_id, trigger_kind, id) WHERE enabled;

CREATE TABLE automation_run (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  project_id bigint NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  rule_id bigint NOT NULL,
  rule_name varchar(80) NOT NULL,
  task_id bigint NOT NULL,
  event_id uuid NOT NULL,
  trigger_kind varchar(24) NOT NULL CHECK (trigger_kind IN ('TASK_MOVED','PULL_REQUEST_MERGED','OVERDUE')),
  outcome varchar(12) NOT NULL CHECK (outcome IN ('APPLIED','SKIPPED','FAILED')),
  detail varchar(255) NOT NULL,
  executed_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(rule_id, task_id, event_id)
);
CREATE INDEX automation_run_project_recent_idx ON automation_run(project_id, id DESC);

CREATE TABLE automation_github (
  project_id bigint PRIMARY KEY REFERENCES projects(id) ON DELETE CASCADE,
  endpoint_id uuid NOT NULL UNIQUE,
  repository varchar(180) NOT NULL,
  encrypted_secret text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

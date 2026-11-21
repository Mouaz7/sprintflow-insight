-- C3 records counts without retaining assignee history or comment contents.
ALTER TABLE task ADD COLUMN risk_block_count integer NOT NULL DEFAULT 0 CHECK (risk_block_count >= 0);
ALTER TABLE task ADD COLUMN risk_reassignment_count integer NOT NULL DEFAULT 0 CHECK (risk_reassignment_count >= 0);

CREATE FUNCTION track_risk_task_changes() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    NEW.risk_block_count := CASE WHEN NEW.is_blocked THEN 1 ELSE 0 END;
    RETURN NEW;
  END IF;
  IF NEW.is_blocked AND NOT OLD.is_blocked THEN
    NEW.risk_block_count := OLD.risk_block_count + 1;
  END IF;
  IF lower(NEW.assignee_email) IS DISTINCT FROM lower(OLD.assignee_email) THEN
    NEW.risk_reassignment_count := OLD.risk_reassignment_count + 1;
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER task_risk_counts BEFORE INSERT OR UPDATE OF is_blocked, assignee_email ON task
FOR EACH ROW EXECUTE FUNCTION track_risk_task_changes();
UPDATE task SET risk_block_count = 1 WHERE is_blocked;

-- Retain the first nightly observation for each task in a sprint. Label only after the outcome.
CREATE TABLE task_risk_observation (
  project_id bigint NOT NULL,
  sprint_id bigint NOT NULL,
  task_id bigint NOT NULL,
  observed_at timestamptz NOT NULL,
  features double precision[] NOT NULL CHECK (cardinality(features) = 8 AND array_ndims(features) = 1),
  resolved_at timestamptz,
  late boolean,
  PRIMARY KEY (project_id, sprint_id, task_id),
  FOREIGN KEY (sprint_id, project_id) REFERENCES sprint(id, project_id) ON DELETE CASCADE,
  FOREIGN KEY (task_id, project_id) REFERENCES task(id, project_id) ON DELETE CASCADE,
  CHECK ((resolved_at IS NULL AND late IS NULL) OR (resolved_at > observed_at AND late IS NOT NULL))
);
CREATE INDEX task_risk_observation_training_idx ON task_risk_observation(project_id, observed_at DESC, sprint_id, task_id) WHERE late IS NOT NULL;
CREATE INDEX task_risk_observation_task_idx ON task_risk_observation(task_id, project_id);

CREATE TABLE task_risk_model (
  project_id bigint PRIMARY KEY REFERENCES projects(id) ON DELETE CASCADE,
  trained_at timestamptz NOT NULL,
  example_count integer NOT NULL CHECK (example_count >= 0),
  source varchar(16) NOT NULL CHECK (source IN ('TRAINED', 'HEURISTIC')),
  intercept double precision NOT NULL,
  means double precision[] NOT NULL CHECK (cardinality(means) = 8 AND array_ndims(means) = 1),
  scales double precision[] NOT NULL CHECK (cardinality(scales) = 8 AND array_ndims(scales) = 1),
  weights double precision[] NOT NULL CHECK (cardinality(weights) = 8 AND array_ndims(weights) = 1)
);

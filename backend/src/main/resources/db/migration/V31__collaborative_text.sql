ALTER TABLE retrospective_entry ADD CONSTRAINT retrospective_entry_id_project UNIQUE (id, project_id);

CREATE TABLE crdt_doc (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  project_id bigint NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  task_id bigint UNIQUE,
  retrospective_id bigint UNIQUE,
  initial_text varchar(1000) NOT NULL,
  revision bigint NOT NULL DEFAULT 0 CHECK (revision BETWEEN 0 AND 16384),
  framed_bytes integer NOT NULL DEFAULT 4 CHECK (framed_bytes BETWEEN 4 AND 2097152),
  snapshot bytea,
  snapshot_revision bigint NOT NULL DEFAULT 0,
  projection_revision bigint NOT NULL DEFAULT 0,
  CHECK (num_nonnulls(task_id, retrospective_id) = 1),
  CHECK (snapshot_revision BETWEEN 0 AND revision AND projection_revision BETWEEN 0 AND revision),
  CHECK ((snapshot IS NULL) = (snapshot_revision = 0)),
  FOREIGN KEY (task_id, project_id) REFERENCES task(id, project_id) ON DELETE CASCADE,
  FOREIGN KEY (retrospective_id, project_id) REFERENCES retrospective_entry(id, project_id) ON DELETE CASCADE
);
CREATE INDEX crdt_doc_project_idx ON crdt_doc(project_id);

-- Receipts stay small and survive snapshot compaction so reconnect retries remain idempotent.
CREATE TABLE crdt_receipt (
  doc_id bigint NOT NULL REFERENCES crdt_doc(id) ON DELETE CASCADE,
  update_id uuid NOT NULL,
  revision bigint NOT NULL CHECK (revision > 0),
  sha256 bytea NOT NULL CHECK (octet_length(sha256) = 32),
  PRIMARY KEY (doc_id, update_id),
  UNIQUE(doc_id, revision)
);
CREATE TABLE crdt_update (
  doc_id bigint NOT NULL,
  revision bigint NOT NULL,
  payload bytea NOT NULL CHECK (octet_length(payload) BETWEEN 1 AND 12288),
  PRIMARY KEY (doc_id, revision),
  FOREIGN KEY (doc_id, revision) REFERENCES crdt_receipt(doc_id, revision) ON DELETE CASCADE
);

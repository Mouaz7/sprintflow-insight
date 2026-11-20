-- B19: retry-safe bounded imports, provenance separate from live activity, and append-only audit.
CREATE TABLE import_batch (
 id UUID PRIMARY KEY, project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
 actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
 request_hash CHAR(64) NOT NULL, format VARCHAR(16) NOT NULL CHECK (format IN ('CSV','JIRA_JSON')),
 created_count INTEGER NOT NULL CHECK (created_count BETWEEN 0 AND 200),
 skipped_count INTEGER NOT NULL CHECK (skipped_count BETWEEN 0 AND 200),
 task_ids JSONB NOT NULL CHECK (jsonb_typeof(task_ids)='array' AND jsonb_array_length(task_ids)<=200),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(project_id,request_hash)
);
CREATE INDEX import_batch_project_time_idx ON import_batch(project_id,created_at DESC,id);
CREATE INDEX import_batch_actor_idx ON import_batch(actor_user_id);
CREATE TABLE import_source (
 task_id BIGINT PRIMARY KEY, project_id BIGINT NOT NULL,
 batch_id UUID NOT NULL REFERENCES import_batch(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
 format VARCHAR(16) NOT NULL, source_key VARCHAR(128),
 source_created_at TIMESTAMPTZ, source_started_at TIMESTAMPTZ, source_completed_at TIMESTAMPTZ,
 original_title TEXT NOT NULL, original_description TEXT,
 history_preserved BOOLEAN NOT NULL,
 FOREIGN KEY(task_id,project_id) REFERENCES task(id,project_id) ON DELETE CASCADE,
 UNIQUE(project_id,format,source_key),
 CHECK(length(original_title)<=8192 AND length(original_description)<=8192)
);
CREATE INDEX import_source_batch_idx ON import_source(batch_id);
-- Deliberately independent of project/account FK cascades: deleting the subject cannot erase its audit.
-- Ids and fixed action metadata are kept, never passwords, uploaded content or email addresses.
CREATE TABLE admin_audit_event (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 project_id BIGINT, actor_user_id BIGINT NOT NULL,
 actor_role VARCHAR(16) NOT NULL CHECK(actor_role IN ('Owner','Admin','Member','Account')),
 action VARCHAR(64) NOT NULL, target_type VARCHAR(32) NOT NULL, target_id VARCHAR(80),
 details JSONB NOT NULL DEFAULT '{}' CHECK(jsonb_typeof(details)='object' AND octet_length(details::text)<=4096),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX admin_audit_project_id_idx ON admin_audit_event(project_id,id DESC);
CREATE INDEX admin_audit_actor_id_idx ON admin_audit_event(actor_user_id,id DESC);
CREATE INDEX admin_audit_retention_idx ON admin_audit_event(created_at,id);
CREATE FUNCTION guard_admin_audit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' AND OLD.created_at < statement_timestamp()-interval '90 days' THEN RETURN OLD; END IF;
 RAISE EXCEPTION 'Audit records are append-only during their 90-day retention period' USING ERRCODE='23514';
END $$;
CREATE TRIGGER admin_audit_append_only BEFORE UPDATE OR DELETE ON admin_audit_event FOR EACH ROW EXECUTE FUNCTION guard_admin_audit();

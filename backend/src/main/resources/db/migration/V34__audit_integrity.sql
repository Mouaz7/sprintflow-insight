CREATE TABLE admin_audit_chain (
    scope_id bigint PRIMARY KEY CHECK (scope_id >= 0),
    head_position bigint NOT NULL DEFAULT 0,
    head_hash bytea NOT NULL DEFAULT decode(repeat('00', 32), 'hex'),
    floor_position bigint NOT NULL DEFAULT 0,
    floor_hash bytea NOT NULL DEFAULT decode(repeat('00', 32), 'hex'),
    CHECK (head_position >= floor_position AND floor_position >= 0),
    CHECK (octet_length(head_hash) = 32 AND octet_length(floor_hash) = 32)
);

ALTER TABLE admin_audit_event
    ALTER COLUMN created_at DROP DEFAULT,
    ADD COLUMN chain_position bigint,
    ADD COLUMN previous_hash bytea,
    ADD COLUMN chain_hash bytea;

CREATE FUNCTION audit_event_bytes(item admin_audit_event) RETURNS bytea
LANGUAGE sql STABLE AS $$
    SELECT convert_to(jsonb_build_array(
        'sprintflow-audit-v1', item.id, item.project_id, item.actor_user_id, item.actor_role,
        item.action, item.target_type, item.target_id, item.details,
        (extract(epoch FROM item.created_at) * 1000000)::bigint,
        item.chain_position
    )::text, 'UTF8')
$$;

-- A stream is identified by its project ID. Scope 0 holds account events.
CREATE FUNCTION link_audit_event(item admin_audit_event) RETURNS admin_audit_event
LANGUAGE plpgsql AS $$
DECLARE
    scope bigint := coalesce(item.project_id, 0);
    head admin_audit_chain;
BEGIN
    INSERT INTO admin_audit_chain(scope_id) VALUES (scope) ON CONFLICT DO NOTHING;
    SELECT * INTO STRICT head FROM admin_audit_chain WHERE scope_id = scope FOR UPDATE;
    IF item.created_at IS NULL THEN
        item.created_at := clock_timestamp();
    END IF;
    item.chain_position := head.head_position + 1;
    item.previous_hash := head.head_hash;
    item.chain_hash := sha256(item.previous_hash || audit_event_bytes(item));
    UPDATE admin_audit_chain SET head_position=item.chain_position, head_hash=item.chain_hash
    WHERE scope_id=scope;
    RETURN item;
END
$$;

-- Keep the original audit fields intact while adding proof metadata to older entries.
ALTER TABLE admin_audit_event DISABLE TRIGGER admin_audit_append_only;
DO $$
DECLARE item admin_audit_event;
BEGIN
    FOR item IN SELECT * FROM admin_audit_event ORDER BY id LOOP
        item := link_audit_event(item);
        UPDATE admin_audit_event
        SET chain_position=item.chain_position, previous_hash=item.previous_hash, chain_hash=item.chain_hash
        WHERE id=item.id;
    END LOOP;
END
$$;
ALTER TABLE admin_audit_event ENABLE TRIGGER admin_audit_append_only;

ALTER TABLE admin_audit_event
    ALTER COLUMN chain_position SET NOT NULL,
    ALTER COLUMN previous_hash SET NOT NULL,
    ALTER COLUMN chain_hash SET NOT NULL,
    ADD CONSTRAINT audit_event_proof_bounds CHECK (
      chain_position > 0 AND octet_length(previous_hash) = 32 AND octet_length(chain_hash) = 32
    );
CREATE UNIQUE INDEX admin_audit_chain_position_idx
    ON admin_audit_event((coalesce(project_id, 0)), chain_position);

CREATE FUNCTION audit_event_insert() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RETURN link_audit_event(NEW);
END
$$;
CREATE TRIGGER admin_audit_chain_before_insert
    BEFORE INSERT ON admin_audit_event FOR EACH ROW EXECUTE FUNCTION audit_event_insert();

CREATE TABLE admin_audit_anchor (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scope_id bigint NOT NULL REFERENCES admin_audit_chain(scope_id),
    anchor_day date NOT NULL,
    position bigint NOT NULL CHECK (position >= 0),
    hash bytea NOT NULL CHECK (octet_length(hash) = 32),
    authenticated_proof text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (scope_id, anchor_day)
);

CREATE OR REPLACE FUNCTION guard_admin_audit() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' AND OLD.created_at < statement_timestamp()-interval '90 days'
        AND EXISTS (SELECT 1 FROM admin_audit_chain
                    WHERE scope_id=coalesce(OLD.project_id,0) AND floor_position>=OLD.chain_position)
    THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'Audit records are append-only until checkpointed by retention' USING ERRCODE='23514';
END
$$;

-- A verified prefix can be removed only after advancing its stored checkpoint.
CREATE FUNCTION prune_audit_history(row_limit integer) RETURNS integer
LANGUAGE plpgsql AS $$
DECLARE
    cutoff timestamptz := statement_timestamp() - interval '90 days';
    scope bigint;
    state admin_audit_chain;
    row_state record;
    advanced integer := 0;
BEGIN
    FOR scope IN
        SELECT coalesce(project_id, 0) FROM admin_audit_event
        WHERE created_at < cutoff GROUP BY coalesce(project_id, 0)
        ORDER BY coalesce(project_id, 0) LIMIT row_limit
    LOOP
        EXIT WHEN advanced >= row_limit;
        SELECT * INTO STRICT state FROM admin_audit_chain WHERE scope_id=scope FOR UPDATE;
        FOR row_state IN
            SELECT chain_position, chain_hash, previous_hash, created_at, audit_event_bytes(e) AS payload
            FROM admin_audit_event e
            WHERE coalesce(project_id, 0)=scope AND chain_position>state.floor_position
            ORDER BY chain_position LIMIT (row_limit - advanced)
        LOOP
            EXIT WHEN row_state.created_at >= cutoff
                OR row_state.chain_position <> state.floor_position + 1
                OR row_state.previous_hash <> state.floor_hash
                OR row_state.chain_hash <> sha256(state.floor_hash || row_state.payload);
            state.floor_position := row_state.chain_position;
            state.floor_hash := row_state.chain_hash;
            advanced := advanced + 1;
        END LOOP;
        UPDATE admin_audit_chain SET floor_position=state.floor_position, floor_hash=state.floor_hash
        WHERE scope_id=scope;
        DELETE FROM admin_audit_event
        WHERE coalesce(project_id, 0)=scope AND chain_position<=state.floor_position;
    END LOOP;
    RETURN advanced;
END
$$;

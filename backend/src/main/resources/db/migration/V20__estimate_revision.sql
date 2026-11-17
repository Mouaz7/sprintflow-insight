-- B3: protect edits to existing PERT estimates from stale browser updates.
-- Epic/dependency/estimate storage was already delivered in the shared V17 prerequisite.
ALTER TABLE task_estimate ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
-- Keep a revision marker after removal so old drafts cannot recreate an estimate or match a
-- replacement's initial revision. Reads and forecasts only include active estimates.
ALTER TABLE task_estimate ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

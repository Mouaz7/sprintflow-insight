-- B9 completion: the epic storage foundation is introduced by the shared B3 V18 migration.
-- Keep this additive migration separate from V16, which existing installations may have applied.
ALTER TABLE epic ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
  setweight(to_tsvector('swedish'::regconfig, coalesce(title, '')), 'A') ||
  setweight(to_tsvector('english'::regconfig, coalesce(title, '')), 'A') ||
  setweight(to_tsvector('simple'::regconfig, coalesce(title, '')), 'A') ||
  setweight(to_tsvector('swedish'::regconfig, coalesce(description, '')), 'B') ||
  setweight(to_tsvector('english'::regconfig, coalesce(description, '')), 'B')
) STORED;
CREATE INDEX epic_search_idx ON epic USING gin (search_vector);
CREATE INDEX epic_title_trgm_idx ON epic USING gin (title extensions.gin_trgm_ops);

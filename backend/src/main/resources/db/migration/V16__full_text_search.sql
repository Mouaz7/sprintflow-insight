-- B9: full-text search over tasks, stories, sprints, comments and projects.
--
-- Each searchable table gets a stored, generated tsvector that combines the Swedish and English
-- stemmers (so "sprintarna" and "sprints" both reach their stem) with an unstemmed 'simple' copy of
-- the title (exact words, names and identifiers). Weights: A = title/name, B = body, C = extra.
-- GIN indexes serve the @@ matches. pg_trgm adds typo tolerance on titles through word_similarity.
--
-- pg_trgm lives in its own schema, and every reference to it is schema-qualified. A search_path
-- independent location keeps the migration valid for any Flyway default schema, and an extension
-- is created only once per database.

CREATE SCHEMA IF NOT EXISTS extensions;

DO $$
DECLARE
  installed_in TEXT;
BEGIN
  SELECT namespace.nspname INTO installed_in
  FROM pg_extension extension JOIN pg_namespace namespace ON namespace.oid = extension.extnamespace
  WHERE extension.extname = 'pg_trgm';
  IF installed_in IS NULL THEN
    CREATE EXTENSION pg_trgm WITH SCHEMA extensions;
  ELSIF installed_in <> 'extensions' THEN
    ALTER EXTENSION pg_trgm SET SCHEMA extensions;
  END IF;
END $$;

-- 1. Tasks: title, description and labels (a JSON array; the parser ignores its punctuation).
ALTER TABLE task ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
  setweight(to_tsvector('swedish'::regconfig, coalesce(title, '')), 'A') ||
  setweight(to_tsvector('english'::regconfig, coalesce(title, '')), 'A') ||
  setweight(to_tsvector('simple'::regconfig, coalesce(title, '')), 'A') ||
  setweight(to_tsvector('swedish'::regconfig, coalesce(description, '')), 'B') ||
  setweight(to_tsvector('english'::regconfig, coalesce(description, '')), 'B') ||
  setweight(to_tsvector('simple'::regconfig, coalesce(labels, '')), 'C')
) STORED;
CREATE INDEX task_search_idx ON task USING gin (search_vector);
CREATE INDEX task_title_trgm_idx ON task USING gin (title extensions.gin_trgm_ops);

-- 2. Stories: title, description and acceptance criteria.
ALTER TABLE user_stories ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
  setweight(to_tsvector('swedish'::regconfig, coalesce(title, '')), 'A') ||
  setweight(to_tsvector('english'::regconfig, coalesce(title, '')), 'A') ||
  setweight(to_tsvector('simple'::regconfig, coalesce(title, '')), 'A') ||
  setweight(to_tsvector('swedish'::regconfig, coalesce(description, '')), 'B') ||
  setweight(to_tsvector('english'::regconfig, coalesce(description, '')), 'B') ||
  setweight(to_tsvector('swedish'::regconfig, coalesce(acceptance_criteria, '')), 'C') ||
  setweight(to_tsvector('english'::regconfig, coalesce(acceptance_criteria, '')), 'C')
) STORED;
CREATE INDEX story_search_idx ON user_stories USING gin (search_vector);
CREATE INDEX story_title_trgm_idx ON user_stories USING gin (title extensions.gin_trgm_ops);

-- 3. Sprints: name and goal.
ALTER TABLE sprint ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
  setweight(to_tsvector('swedish'::regconfig, coalesce(name, '')), 'A') ||
  setweight(to_tsvector('english'::regconfig, coalesce(name, '')), 'A') ||
  setweight(to_tsvector('simple'::regconfig, coalesce(name, '')), 'A') ||
  setweight(to_tsvector('swedish'::regconfig, coalesce(goal, '')), 'B') ||
  setweight(to_tsvector('english'::regconfig, coalesce(goal, '')), 'B')
) STORED;
CREATE INDEX sprint_search_idx ON sprint USING gin (search_vector);
CREATE INDEX sprint_name_trgm_idx ON sprint USING gin (name extensions.gin_trgm_ops);

-- 4. Comments: the message body.
ALTER TABLE task_comment ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
  setweight(to_tsvector('swedish'::regconfig, coalesce(message, '')), 'B') ||
  setweight(to_tsvector('english'::regconfig, coalesce(message, '')), 'B') ||
  setweight(to_tsvector('simple'::regconfig, coalesce(message, '')), 'B')
) STORED;
CREATE INDEX task_comment_search_idx ON task_comment USING gin (search_vector);
CREATE INDEX task_comment_task_idx ON task_comment (task_id);

-- 5. Projects: name, key and description.
ALTER TABLE projects ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
  setweight(to_tsvector('swedish'::regconfig, coalesce(name, '')), 'A') ||
  setweight(to_tsvector('english'::regconfig, coalesce(name, '')), 'A') ||
  setweight(to_tsvector('simple'::regconfig, coalesce(name, '') || ' ' || coalesce(key, '')), 'A') ||
  setweight(to_tsvector('swedish'::regconfig, coalesce(description, '')), 'B') ||
  setweight(to_tsvector('english'::regconfig, coalesce(description, '')), 'B')
) STORED;
CREATE INDEX project_search_idx ON projects USING gin (search_vector);
CREATE INDEX project_name_trgm_idx ON projects USING gin (name extensions.gin_trgm_ops);

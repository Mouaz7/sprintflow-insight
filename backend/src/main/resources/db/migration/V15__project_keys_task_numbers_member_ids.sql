-- B0: project keys (SF), per-project task numbers (SF-123) and membership by immutable account id.
-- Existing rows are backfilled deterministically; no row versions change.

-- 1. Project keys -------------------------------------------------------------------------------
-- Session-local helpers (pg_temp), dropped again below. Accented letters are folded explicitly in
-- both cases because upper() leaves them unchanged under the C locale.
CREATE FUNCTION pg_temp.derive_project_key(project_name TEXT) RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE
  cleaned TEXT := btrim(regexp_replace(
    translate(upper(coalesce(project_name, '')),
              'ÅÄÀÁÂÃÆÖÒÓÔÕØÉÈÊËÜÙÚÛÍÌÎÏÑÇÝåäàáâãæöòóôõøéèêëüùúûíìîïñçý',
              'AAAAAAAOOOOOOEEEEUUUUIIIINCYAAAAAAAOOOOOOEEEEUUUUIIIINCY'),
    '[^A-Z]+', ' ', 'g'));
  words TEXT[];
  result TEXT := '';
BEGIN
  IF cleaned = '' THEN RETURN 'PRJ'; END IF;
  words := string_to_array(cleaned, ' ');
  IF array_length(words, 1) >= 2 THEN
    FOR i IN 1..least(array_length(words, 1), 4) LOOP
      result := result || left(words[i], 1);
    END LOOP;
  ELSE
    result := left(words[1], 4);
  END IF;
  IF length(result) < 2 THEN RETURN 'PRJ'; END IF;
  RETURN result;
END $$;

-- Bijective base-26 suffix: 1 -> A, 26 -> Z, 27 -> AA. Same rule as ProjectKeys.suffix in Java.
CREATE FUNCTION pg_temp.key_suffix(n INTEGER) RETURNS TEXT LANGUAGE plpgsql AS $$
DECLARE
  remaining INTEGER := n;
  result TEXT := '';
BEGIN
  WHILE remaining > 0 LOOP
    remaining := remaining - 1;
    result := chr(65 + remaining % 26) || result;
    remaining := remaining / 26;
  END LOOP;
  RETURN result;
END $$;

ALTER TABLE projects ADD COLUMN key VARCHAR(10);

DO $$
DECLARE
  project RECORD;
  base TEXT;
  candidate TEXT;
  attempt INTEGER;
BEGIN
  FOR project IN SELECT id, name FROM projects ORDER BY id LOOP
    base := pg_temp.derive_project_key(project.name);
    candidate := base;
    attempt := 0;
    WHILE EXISTS (SELECT 1 FROM projects WHERE key = candidate) LOOP
      attempt := attempt + 1;
      candidate := left(base, 10 - length(pg_temp.key_suffix(attempt))) || pg_temp.key_suffix(attempt);
    END LOOP;
    UPDATE projects SET key = candidate WHERE id = project.id;
  END LOOP;
END $$;

DROP FUNCTION pg_temp.derive_project_key(TEXT);
DROP FUNCTION pg_temp.key_suffix(INTEGER);

ALTER TABLE projects ALTER COLUMN key SET NOT NULL;
ALTER TABLE projects ADD CONSTRAINT projects_key_format CHECK (key ~ '^[A-Z]{2,10}$');
ALTER TABLE projects ADD CONSTRAINT projects_key_unique UNIQUE (key);

-- 2. Task numbers -------------------------------------------------------------------------------
ALTER TABLE task ADD COLUMN number INTEGER;
UPDATE task SET number = numbered.position
FROM (SELECT id, row_number() OVER (PARTITION BY project_id ORDER BY id) AS position FROM task) numbered
WHERE numbered.id = task.id;
ALTER TABLE task ALTER COLUMN number SET NOT NULL;
ALTER TABLE task ADD CONSTRAINT task_number_positive CHECK (number > 0);
ALTER TABLE task ADD CONSTRAINT task_project_number_unique UNIQUE (project_id, number);

-- One counter row per project. Allocation is a single INSERT .. ON CONFLICT DO UPDATE .. RETURNING,
-- which takes the row lock, so concurrent creators always receive distinct numbers. Numbers are
-- never reused, also not after a task is permanently deleted.
CREATE TABLE project_task_counter (
  project_id BIGINT PRIMARY KEY REFERENCES projects(id) ON DELETE CASCADE,
  last_number INTEGER NOT NULL CHECK (last_number >= 0)
);
INSERT INTO project_task_counter(project_id, last_number)
SELECT project.id, coalesce(max(task.number), 0)
FROM projects project LEFT JOIN task ON task.project_id = project.id
GROUP BY project.id;

-- 3. Membership by account id -------------------------------------------------------------------
ALTER TABLE project_member ADD COLUMN user_id BIGINT;
UPDATE project_member member SET user_id = account.id
FROM users account WHERE lower(account.email) = lower(member.email);

-- A row without an account grants nothing today, but would silently grant access to whoever later
-- registers that address. Remove it instead of carrying the email-based behaviour forward.
DELETE FROM project_member WHERE user_id IS NULL;

-- Case variants of one address collapse to one account: keep the most privileged row.
DELETE FROM project_member member USING (
  SELECT id, row_number() OVER (
    PARTITION BY project_id, user_id
    ORDER BY CASE lower(role) WHEN 'owner' THEN 0 WHEN 'admin' THEN 1 WHEN 'member' THEN 2 ELSE 3 END, id
  ) AS position
  FROM project_member) ranked
WHERE ranked.id = member.id AND ranked.position > 1;

-- Rows written with explicit ids (imports, restores) can leave the identity behind its data.
SELECT setval(pg_get_serial_sequence('project_member', 'id'),
              greatest((SELECT max(id) FROM project_member), 1));

-- Membership is now the single source of access, so every owner needs an Owner row ...
INSERT INTO project_member(project_id, email, role, user_id)
SELECT project.id, account.email, 'Owner', account.id
FROM projects project JOIN users account ON lower(account.email) = lower(project.owner_email)
WHERE NOT EXISTS (
  SELECT 1 FROM project_member member WHERE member.project_id = project.id AND member.user_id = account.id);
UPDATE project_member member SET role = 'Owner'
FROM projects project JOIN users account ON lower(account.email) = lower(project.owner_email)
WHERE member.project_id = project.id AND member.user_id = account.id AND member.role <> 'Owner';
-- ... and only the owner has one (ownership transfer demotes the previous owner to Member).
UPDATE project_member member SET role = 'Member'
FROM projects project
WHERE member.project_id = project.id AND lower(member.role) = 'owner'
  AND NOT EXISTS (
    SELECT 1 FROM users account
    WHERE account.id = member.user_id AND lower(account.email) = lower(project.owner_email));
-- The email column stays as a display copy of the account address.
UPDATE project_member member SET email = account.email
FROM users account WHERE account.id = member.user_id AND member.email <> account.email;

ALTER TABLE project_member ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE project_member ADD CONSTRAINT project_member_user_fk
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE project_member ADD CONSTRAINT project_member_project_user_unique UNIQUE (project_id, user_id);
CREATE INDEX project_member_user_idx ON project_member(user_id);

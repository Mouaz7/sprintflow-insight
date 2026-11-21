CREATE TABLE organization (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug varchar(80) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,78}[a-z0-9]$'),
    name varchar(120) NOT NULL CHECK (length(btrim(name)) > 0),
    personal_for_user_id bigint UNIQUE REFERENCES users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE organization_member (
    organization_id bigint NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    user_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role varchar(10) NOT NULL CHECK (role IN ('Owner', 'Admin', 'Member', 'Viewer')),
    PRIMARY KEY (organization_id, user_id)
);
CREATE INDEX organization_member_user_idx ON organization_member(user_id, organization_id);
CREATE UNIQUE INDEX organization_single_owner_idx
    ON organization_member(organization_id) WHERE role = 'Owner';

INSERT INTO organization(slug, name, personal_for_user_id)
SELECT 'personal-' || id, 'Personal workspace', id FROM users;
INSERT INTO organization_member(organization_id, user_id, role)
SELECT id, personal_for_user_id, 'Owner' FROM organization;

-- Historical fixtures and imported legacy projects may lack a resolvable account.
INSERT INTO organization(slug, name) VALUES ('legacy-unassigned', 'Legacy projects');

ALTER TABLE projects ADD COLUMN organization_id bigint REFERENCES organization(id);
UPDATE projects p
SET organization_id = o.id
FROM organization o JOIN users u ON u.id = o.personal_for_user_id
WHERE lower(u.email) = lower(p.owner_email);
UPDATE projects p
SET organization_id = o.id
FROM project_member m JOIN organization o ON o.personal_for_user_id = m.user_id
WHERE p.organization_id IS NULL AND m.project_id = p.id AND m.role = 'Owner';
UPDATE projects
SET organization_id = (SELECT id FROM organization WHERE slug = 'legacy-unassigned')
WHERE organization_id IS NULL;

ALTER TABLE projects
    ALTER COLUMN organization_id SET NOT NULL,
    DROP CONSTRAINT projects_name_key,
    ADD CONSTRAINT projects_organization_name_unique UNIQUE (organization_id, name);

CREATE FUNCTION initialize_personal_organization() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE org_id bigint;
BEGIN
    INSERT INTO organization(slug, name, personal_for_user_id)
    VALUES ('personal-' || NEW.id, 'Personal workspace', NEW.id) RETURNING id INTO org_id;
    INSERT INTO organization_member(organization_id, user_id, role)
    VALUES (org_id, NEW.id, 'Owner');
    RETURN NEW;
END
$$;
CREATE TRIGGER user_initializes_personal_organization
    AFTER INSERT ON users FOR EACH ROW EXECUTE FUNCTION initialize_personal_organization();

CREATE FUNCTION default_project_organization() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.organization_id IS NULL THEN
        SELECT o.id INTO NEW.organization_id
        FROM organization o JOIN users u ON u.id = o.personal_for_user_id
        WHERE lower(u.email) = lower(NEW.owner_email);
        IF NEW.organization_id IS NULL THEN
            SELECT id INTO NEW.organization_id FROM organization WHERE slug = 'legacy-unassigned';
        END IF;
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER project_gets_default_organization
    BEFORE INSERT ON projects FOR EACH ROW EXECUTE FUNCTION default_project_organization();

CREATE TABLE team (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id bigint NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    name varchar(120) NOT NULL CHECK (length(btrim(name)) > 0),
    UNIQUE (id, organization_id)
);
CREATE UNIQUE INDEX team_org_name_unique ON team(organization_id, lower(name));

CREATE TABLE team_member (
    team_id bigint NOT NULL,
    organization_id bigint NOT NULL,
    user_id bigint NOT NULL,
    PRIMARY KEY (team_id, user_id),
    FOREIGN KEY (team_id, organization_id) REFERENCES team(id, organization_id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id, user_id)
        REFERENCES organization_member(organization_id, user_id) ON DELETE CASCADE
);
CREATE INDEX team_member_org_user_idx ON team_member(organization_id, user_id, team_id);

CREATE TABLE organization_invite (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id bigint NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    token_digest bytea NOT NULL UNIQUE CHECK (octet_length(token_digest) = 32),
    email varchar(255) NOT NULL CHECK (email = lower(email)),
    role varchar(10) NOT NULL CHECK (role IN ('Admin', 'Member', 'Viewer')),
    created_by bigint REFERENCES users(id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    CHECK (expires_at > created_at)
);
CREATE INDEX organization_invite_org_idx ON organization_invite(organization_id, expires_at);

CREATE TABLE organization_template (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id bigint NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    name varchar(120) NOT NULL CHECK (length(btrim(name)) > 0),
    template_kind varchar(10) NOT NULL CHECK (template_kind IN ('Blank', 'Scrum', 'Kanban')),
    description varchar(255)
);
CREATE UNIQUE INDEX organization_template_name_unique
    ON organization_template(organization_id, lower(name));

-- The application binds the verified organization for the duration of the transaction.
CREATE FUNCTION current_organization_scope() RETURNS bigint
LANGUAGE sql STABLE AS $$
    SELECT nullif(current_setting('app.org_id', true), '')::bigint
$$;

ALTER TABLE team ENABLE ROW LEVEL SECURITY;
ALTER TABLE team FORCE ROW LEVEL SECURITY;
CREATE POLICY team_organization_scope ON team
    USING (organization_id = current_organization_scope())
    WITH CHECK (organization_id = current_organization_scope());

ALTER TABLE team_member ENABLE ROW LEVEL SECURITY;
ALTER TABLE team_member FORCE ROW LEVEL SECURITY;
CREATE POLICY team_member_organization_scope ON team_member
    USING (organization_id = current_organization_scope())
    WITH CHECK (organization_id = current_organization_scope());

ALTER TABLE organization_invite ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_invite FORCE ROW LEVEL SECURITY;
CREATE POLICY organization_invite_scope ON organization_invite
    USING (organization_id = current_organization_scope())
    WITH CHECK (organization_id = current_organization_scope());

ALTER TABLE organization_template ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_template FORCE ROW LEVEL SECURITY;
CREATE POLICY organization_template_scope ON organization_template
    USING (organization_id = current_organization_scope())
    WITH CHECK (organization_id = current_organization_scope());

-- Compose and Testcontainers bootstrap with a superuser. Use a narrowly granted
-- role for these queries in that environment so PostgreSQL applies the policies.
DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = current_user AND (rolsuper OR rolbypassrls)) THEN
        BEGIN
            CREATE ROLE sprintflow_organization_scope NOLOGIN NOSUPERUSER NOBYPASSRLS NOINHERIT;
        EXCEPTION WHEN duplicate_object THEN
            NULL;
        END;
        IF EXISTS (
            SELECT FROM pg_roles WHERE rolname = 'sprintflow_organization_scope'
            AND (rolsuper OR rolbypassrls OR rolcanlogin OR rolinherit)
        ) THEN
            RAISE EXCEPTION 'sprintflow_organization_scope must be a restricted non-login role';
        END IF;
        GRANT sprintflow_organization_scope TO CURRENT_USER;
        EXECUTE format('GRANT USAGE ON SCHEMA %I TO sprintflow_organization_scope', current_schema());
        GRANT SELECT, INSERT, UPDATE, DELETE ON team, team_member, organization_invite, organization_template
            TO sprintflow_organization_scope;
        GRANT SELECT (id, email, display_name) ON users TO sprintflow_organization_scope;
        GRANT SELECT (organization_id, user_id, role) ON organization_member TO sprintflow_organization_scope;
        GRANT USAGE ON SEQUENCE team_id_seq, organization_invite_id_seq, organization_template_id_seq
            TO sprintflow_organization_scope;
    END IF;
END
$$;

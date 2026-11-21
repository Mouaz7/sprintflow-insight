# ADR 0034 — Organizations, teams and row-level security

Status accepted.

V32 assigns an organization to every existing project. Registered accounts have a personal organization. Project names are unique within their parent organization, and project keys remain globally unique for task links. The legacy project views and organization project lists use existing project membership when displaying project contents.

Organization owners, admins, members and viewers use a separate membership directory. Owners and admins manage teams, invitations and templates. An owner appoints admins and may transfer a shared organization to an existing member or admin. Members can create projects and viewers can browse the organization and their accessible projects. Team member foreign keys require membership in the same organization. Account deletion requires transferring shared ownership or removing other members from a personal organization.

The application checks organization membership and privileges before setting `app.org_id` for the current transaction. PostgreSQL applies row-level security to team rows, team members, invitations and templates. FORCE ROW LEVEL SECURITY includes table owners. PostgreSQL's bootstrap accounts can bypass these policies. With such an account, feature queries temporarily adopt the non-login `sprintflow_organization_scope` role with access to the protected tables and member directory fields. SQL checks the usual route and resource ownership even with RLS enabled. [PostgreSQL row security policies](https://www.postgresql.org/docs/16/ddl-rowsecurity.html).

The server creates a 256-bit, single-use invitation and stores only its SHA-256 digest. The recipient signs in with the named email address. The same transaction checks the expiry and organization, consumes the invitation and adds the membership. The client stores the token in the link fragment so a page request excludes it. Creating an invitation for the same person invalidates their previous link.

Organization templates store a label, starter layout and optional description. Instantiation uses the normal project creation and seed paths for Blank, Scrum and Kanban. [Workspace guide](../workspace-features.md) and [REST contract](../openapi.yaml).

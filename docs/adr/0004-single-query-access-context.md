# 0004 – Single-query access context with declared needs

**Status:** Accepted (phase A1)

## Context
Authorisation used several helpers (`read`, `write`, `owner`, `adminOrOwner`), each loading the project and membership again. A handler could spend five queries before doing any work. Listing sprints without a project ran 1 + 2N queries over every tenant's sprints. Archive protection lived only in `write`, so member management, ownership transfer, report sharing and activity edits worked on archived projects. Any admin could demote or remove another admin.

## Decision
- `ProjectAccessService.require(projectId, session, Need)` loads an `AccessContext` (owner, archive and trash state, and the caller's `ProjectRole`) with one left-join query. It is memoised for the HTTP request, and so is the session email.
- `Need` declares the rule at the call site: `READ`, `WRITE`, `MANAGE`, `MANAGE_ARCHIVED`, `OWNER` and `OWNER_ARCHIVED`. Only the `*_ARCHIVED` needs are allowed on archived projects (archive toggling, report listing and revocation, the recycle bin).
- Admins manage members and viewers. Only the owner appoints, changes or removes admins; an admin may still step down or leave.
- Cross-project listings are single visibility queries (`SprintRepository.findVisibleTo`).

## Consequences
- The cost of authorisation is constant and tested: `ProjectAccessIntegrationTest` asserts the same statement count for 2 and 8 projects.
- The old helper methods remain as thin delegates, until phase A3 moves callers into feature services.

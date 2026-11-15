# ADR 0016: PostgreSQL full-text search with trigram fallback and in-statement access filtering

Status: accepted for B9.

## Context

B9 adds a search across tasks, stories, sprints, comments and projects, reached from a Ctrl/⌘+K command palette. Content is mixed Swedish and English. Users type fragments ("inlogg"), make typos and paste task keys (`SF-123`). Results must never include another project's data, and the request must stay within the performance budget (p95 < 200 ms with 5 000 tasks per project) without N+1 queries.

## Decision

- **PostgreSQL full-text search, not a separate engine.** Elasticsearch/OpenSearch or Lucene would add an operated service, a second copy of the data, an indexing pipeline with its own consistency lag and a second place to enforce access. The data is already in PostgreSQL 16, and the project's volume is far below where a dedicated engine is needed.
- **Stored generated `tsvector` columns** (migration V16) on `task`, `user_stories`, `sprint`, `task_comment` and `projects`, each with a GIN index. Each vector concatenates the `swedish` and `english` stemmers plus an unstemmed `simple` copy of the title, weighted A (title/name), B (body) and C (labels, acceptance criteria). Generated columns cannot drift from the source text and need no triggers; the JPA entities do not map them.
- **pg_trgm for typos**: GIN trigram indexes on titles and names; a row also matches when `word_similarity(query, title)` reaches pg_trgm's default threshold (0.6). The extension is installed in a dedicated `extensions` schema and every reference is schema-qualified, so migrations are independent of `search_path` (Flyway's per-schema migrations in tests rely on this). An existing installation elsewhere is moved there.
- **Query parsing in Java, never `to_tsquery` on raw input.** `SearchQuery` keeps at most 8 words of letters and digits (≤ 64 code points each, NFKC-folded and lower-cased) and builds `'word':* & …`. Operators, quotes and backslashes cannot reach PostgreSQL's query parser. A text that is exactly a task key is also parsed with B0's `TaskKey`.
- **One statement per search.** A materialised CTE selects the caller's readable projects (membership by `user_id`, `trashed_at IS NULL`, archived included); every branch joins it, so filtering happens before ranking and limiting, not after fetching. An exact task-key hit is a separate branch with rank 1000 and is excluded from the text branch. `ts_headline` runs only on the final page.
- **Rank = `ts_rank` + trigram word similarity**, rounded to six decimals in SQL; ties broken by type and id. **Keyset pagination** on `(rank, type, id)` with an opaque base64url cursor, so deep pages cost the same as the first and results do not shift under concurrent inserts as with OFFSET.
- **Highlights as data, not markup.** `ts_headline` uses control characters as markers (stripped from the source text first); the service turns them into `{text, match}` segments. The client renders text nodes, so stored content can never become HTML.
- A separate Modulith module `search` that depends only on `workspace :: project` (access), `workspace :: task` (`TaskKey`) and `common :: errors`.

## Consequences

- Writes to the five tables compute the vectors (a few microseconds per row) and maintain extra GIN indexes; V16 rewrites the tables once to fill the generated columns.
- `CREATE EXTENSION pg_trgm` needs the database owner (pg_trgm is a trusted extension since PostgreSQL 13) or a superuser. Managed databases that forbid it must pre-install pg_trgm before upgrading.
- Mixing two stemmers can over-match (an English stem matching a Swedish word); ranking weights titles over bodies to limit the effect. Language detection per row was rejected as unreliable for short titles.
- Trigram tolerance applies to titles and names only; typos in descriptions and comments are not found. Transpositions in very short words fall below the threshold.
- An explicit `projectId` answers 403/404 like the project's other reads, which reveals whether a project id exists; the all-projects search reveals nothing.
- The query count is constant (1 statement, 3 with `projectId`) and is asserted by `SearchIntegrationTest`.

## Sources

- PostgreSQL 16 documentation: chapter 12 (Full Text Search), F.35 (pg_trgm), 5.4 (Generated Columns).
- Winand M. *Use the Index, Luke*: "Paging Through Results" (keyset pagination).

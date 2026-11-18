/**
 * Full-text search across the projects a caller can read (B9): tasks, stories, sprints, comments
 * and projects. PostgreSQL does the matching (Swedish and English tsvector columns plus pg_trgm for
 * typos) and the access filtering, in one statement per request. See ADR 0016.
 */
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"common :: errors", "workspace :: project", "workspace :: task"})
package com.sprintflowinsight.search;

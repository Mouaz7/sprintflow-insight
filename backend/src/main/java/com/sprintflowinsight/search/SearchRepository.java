package com.sprintflowinsight.search;

import com.sprintflowinsight.workspace.customfield.PreparedFieldFilter;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * The search statement. Access filtering, matching, ranking, keyset pagination and highlighting all
 * happen in one native query, so a search costs one round trip whatever the number of projects or
 * hits.
 *
 * <p>Rank = {@code ts_rank} over the weighted tsvector + trigram word similarity of the title,
 * rounded to six decimals so the keyset comparison is exact. An exact task-key hit ranks {@value
 * #EXACT_RANK} and is removed from the text branch, so it appears once.
 */
@Repository
public class SearchRepository {
  static final int EXACT_RANK = 1000;

  /** Marks matched words inside {@code ts_headline} output; stripped from the source text first. */
  static final char START = '\u0002';

  static final char STOP = '\u0003';

  private static final String MARKERS = "StartSel=" + START + ", StopSel=" + STOP;
  private static final String SNIPPET_OPTIONS =
      MARKERS + ", MaxWords=20, MinWords=8, ShortWord=2, MaxFragments=1";
  private static final String TITLE_OPTIONS = MARKERS + ", HighlightAll=true";

  private static final String SQL =
      """
      WITH readable AS MATERIALIZED (
        SELECT project.id, project.key, project.name
        FROM projects project
        JOIN project_member member ON member.project_id = project.id AND member.user_id = :userId
        WHERE project.trashed_at IS NULL AND (:projectId = 0 OR project.id = :projectId)
      ),
      query AS (
        SELECT to_tsquery('swedish', :terms) || to_tsquery('english', :terms)
               || to_tsquery('simple', :terms) AS terms
      ),
      hits AS (
        SELECT 5 AS kind, task.id, task.project_id, task.id AS task_id, task.number,
               task.title, task.description AS body, %d AS score, true AS exact
        FROM task JOIN readable ON readable.id = task.project_id
        WHERE :wantTask AND :hasKey AND task.trashed_at IS NULL
          AND /* customFieldFilter */
          AND readable.key = :keyProject AND task.number = :keyNumber
        UNION ALL
        SELECT 5, task.id, task.project_id, task.id, task.number, task.title, task.description,
               ts_rank(task.search_vector, query.terms)
                 + extensions.word_similarity(:text, task.title), false
        FROM task JOIN readable ON readable.id = task.project_id CROSS JOIN query
        WHERE :wantTask AND task.trashed_at IS NULL
          AND /* customFieldFilter */
          AND (task.search_vector @@ query.terms OR :text OPERATOR(extensions.<%%) task.title)
          AND NOT (:hasKey AND readable.key = :keyProject AND task.number = :keyNumber)
        UNION ALL
        SELECT 6, epic.id, epic.project_id, NULL, NULL, epic.title, epic.description,
               ts_rank(epic.search_vector, query.terms)
                 + extensions.word_similarity(:text, epic.title), false
        FROM epic JOIN readable ON readable.id = epic.project_id CROSS JOIN query
        WHERE :wantEpic
          AND (epic.search_vector @@ query.terms OR :text OPERATOR(extensions.<%%) epic.title)
        UNION ALL
        SELECT 4, story.id, story.project_id, NULL, NULL, story.title, story.description,
               ts_rank(story.search_vector, query.terms)
                 + extensions.word_similarity(:text, story.title), false
        FROM user_stories story JOIN readable ON readable.id = story.project_id CROSS JOIN query
        WHERE :wantStory AND story.trashed_at IS NULL
          AND (story.search_vector @@ query.terms OR :text OPERATOR(extensions.<%%) story.title)
        UNION ALL
        SELECT 3, sprint.id, sprint.project_id, NULL, NULL, sprint.name, sprint.goal,
               ts_rank(sprint.search_vector, query.terms)
                 + extensions.word_similarity(:text, sprint.name), false
        FROM sprint JOIN readable ON readable.id = sprint.project_id CROSS JOIN query
        WHERE :wantSprint AND sprint.trashed_at IS NULL
          AND (sprint.search_vector @@ query.terms OR :text OPERATOR(extensions.<%%) sprint.name)
        UNION ALL
        SELECT 2, comment.id, comment.project_id, task.id, task.number, task.title,
               comment.message, ts_rank(comment.search_vector, query.terms), false
        FROM task_comment comment
        JOIN task ON task.id = comment.task_id AND task.project_id = comment.project_id
        JOIN readable ON readable.id = comment.project_id CROSS JOIN query
        WHERE :wantComment AND task.trashed_at IS NULL AND comment.search_vector @@ query.terms
        UNION ALL
        SELECT 1, project.id, project.id, NULL, NULL, project.name, project.description,
               ts_rank(project.search_vector, query.terms)
                 + extensions.word_similarity(:text, project.name), false
        FROM projects project JOIN readable ON readable.id = project.id CROSS JOIN query
        WHERE :wantProject
          AND (project.search_vector @@ query.terms
               OR :text OPERATOR(extensions.<%%) project.name)
      ),
      ranked AS (
        SELECT hits.*, round(CAST(hits.score AS numeric), 6) AS rank FROM hits
      ),
      page AS (
        SELECT * FROM ranked
        WHERE NOT :hasCursor OR (rank, kind, id) < (:cursorRank, :cursorKind, :cursorId)
        ORDER BY rank DESC, kind DESC, id DESC
        LIMIT :limit + 1
      )
      SELECT page.kind, page.id, page.project_id, readable.key, readable.name, page.task_id,
             page.number, page.title, page.rank, page.exact,
             ts_headline('simple', translate(coalesce(page.title, ''), chr(2) || chr(3), ''),
                         query.terms, :titleOptions),
             CASE WHEN coalesce(page.body, '') = '' THEN ''
                  ELSE ts_headline('simple', translate(page.body, chr(2) || chr(3), ''),
                                   query.terms, :snippetOptions) END
      FROM page JOIN readable ON readable.id = page.project_id CROSS JOIN query
      ORDER BY page.rank DESC, page.kind DESC, page.id DESC
      """
          .formatted(EXACT_RANK);

  private final EntityManager entities;

  public SearchRepository(EntityManager entities) {
    this.entities = entities;
  }

  /**
   * At most {@code limit} + 1 hits after {@code cursor} (the extra row tells whether another page
   * exists) in the projects {@code userId} is a member of.
   *
   * @param projectId one project, or {@code null} for every readable project
   * @param cursor {@code null} for the first page
   */
  public List<Row> search(
      long userId,
      Long projectId,
      SearchQuery query,
      Set<SearchType> types,
      SearchCursor cursor,
      int limit) {
    return search(userId, projectId, query, types, cursor, limit, null);
  }

  public List<Row> search(
      long userId,
      Long projectId,
      SearchQuery query,
      Set<SearchType> types,
      SearchCursor cursor,
      int limit,
      PreparedFieldFilter filter) {
    var key = query.taskKey();
    var statement =
        entities.createNativeQuery(
            SQL.replace(
                "/* customFieldFilter */", filter == null ? "true" : filter.taskPredicate()));
    if (filter != null) filter.bind(statement);
    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        statement
            .setParameter("userId", userId)
            .setParameter("projectId", projectId == null ? 0L : projectId)
            .setParameter("terms", query.tsquery())
            .setParameter("text", query.text())
            .setParameter("hasKey", key.isPresent())
            .setParameter("keyProject", key.map(k -> k.projectKey()).orElse(""))
            .setParameter("keyNumber", key.map(k -> k.number()).orElse(0))
            .setParameter("wantEpic", types.contains(SearchType.EPIC))
            .setParameter("wantTask", types.contains(SearchType.TASK))
            .setParameter("wantStory", types.contains(SearchType.STORY))
            .setParameter("wantSprint", types.contains(SearchType.SPRINT))
            .setParameter("wantComment", types.contains(SearchType.COMMENT))
            .setParameter("wantProject", types.contains(SearchType.PROJECT))
            .setParameter("hasCursor", cursor != null)
            .setParameter("cursorRank", cursor == null ? BigDecimal.ZERO : cursor.rank())
            .setParameter("cursorKind", cursor == null ? 0 : cursor.typeOrder())
            .setParameter("cursorId", cursor == null ? 0L : cursor.id())
            .setParameter("limit", limit)
            .setParameter("titleOptions", TITLE_OPTIONS)
            .setParameter("snippetOptions", SNIPPET_OPTIONS)
            .getResultList();
    return rows.stream().map(Row::of).toList();
  }

  /** One raw hit; marked text still contains the {@link #START}/{@link #STOP} markers. */
  public record Row(
      int typeOrder,
      long id,
      long projectId,
      String projectKey,
      String projectName,
      Long taskId,
      Integer number,
      String title,
      BigDecimal rank,
      boolean exact,
      String markedTitle,
      String markedSnippet) {

    static Row of(Object[] column) {
      return new Row(
          ((Number) column[0]).intValue(),
          ((Number) column[1]).longValue(),
          ((Number) column[2]).longValue(),
          (String) column[3],
          (String) column[4],
          column[5] == null ? null : ((Number) column[5]).longValue(),
          column[6] == null ? null : ((Number) column[6]).intValue(),
          (String) column[7],
          (BigDecimal) column[8],
          (Boolean) column[9],
          (String) column[10],
          (String) column[11]);
    }
  }
}

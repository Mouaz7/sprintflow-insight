package com.sprintflowinsight.search;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.search.SearchHit.Segment;
import com.sprintflowinsight.workspace.customfield.CustomFieldService;
import com.sprintflowinsight.workspace.customfield.PreparedFieldFilter;
import com.sprintflowinsight.workspace.project.AccessContext.Need;
import com.sprintflowinsight.workspace.project.ProjectAccessService;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Searches the caller's readable projects. Readability is decided inside the search statement
 * (membership by account id, trashed projects excluded, archived projects included like every other
 * read). An explicit {@code projectId} is checked first through {@link ProjectAccessService}, so an
 * unknown or foreign project answers 404/403 exactly as the project's other read endpoints do.
 */
@Service
public class SearchService {
  private final SearchRepository repository;
  private final ProjectAccessService access;
  private final CustomFieldService customFields;

  public SearchService(
      SearchRepository repository, ProjectAccessService access, CustomFieldService customFields) {
    this.repository = repository;
    this.access = access;
    this.customFields = customFields;
  }

  @Transactional(readOnly = true)
  public SearchResponse search(SearchRequest request, HttpSession session) {
    Long userId = access.userId(session);
    if (request.projectId() != null) access.require(request.projectId(), session, Need.READ);
    var condition = request.customCondition();
    if (condition != null && request.projectId() == null)
      throw ApiException.badRequest("Choose a project before filtering by a custom field.");
    PreparedFieldFilter filter =
        condition == null ? null : customFields.prepare(request.projectId(), condition, session);
    SearchCursor cursor = request.cursor() == null ? null : SearchCursor.decode(request.cursor());
    SearchQuery query = SearchQuery.parse(request.q());
    if (query.isEmpty()) return SearchResponse.empty();

    int pageSize = request.pageSize();
    var types = request.typeSet();
    if (filter != null)
      types =
          types.contains(SearchType.TASK)
              ? java.util.EnumSet.of(SearchType.TASK)
              : java.util.EnumSet.noneOf(SearchType.class);
    List<SearchRepository.Row> rows =
        repository.search(userId, request.projectId(), query, types, cursor, pageSize, filter);
    boolean more = rows.size() > pageSize;
    List<SearchRepository.Row> page = more ? rows.subList(0, pageSize) : rows;
    String next = null;
    if (more) {
      SearchRepository.Row last = page.getLast();
      next = new SearchCursor(last.rank(), last.typeOrder(), last.id()).encode();
    }
    return new SearchResponse(page.stream().map(SearchService::hit).toList(), next);
  }

  private static SearchHit hit(SearchRepository.Row row) {
    SearchType type = SearchType.fromOrder(row.typeOrder());
    String key = row.number() == null ? null : row.projectKey() + "-" + row.number();
    return new SearchHit(
        type.wire(),
        row.id(),
        row.projectId(),
        row.projectKey(),
        row.projectName(),
        row.taskId(),
        key,
        row.title() == null ? "" : row.title(),
        segments(row.markedTitle()),
        segments(row.markedSnippet()),
        row.exact());
  }

  /**
   * Splits {@code ts_headline} output into plain segments. Markers were stripped from the source
   * text in SQL, so every marker here came from PostgreSQL; an unbalanced marker is tolerated.
   */
  static List<Segment> segments(String marked) {
    List<Segment> segments = new ArrayList<>();
    if (marked == null || marked.isEmpty()) return segments;
    StringBuilder current = new StringBuilder();
    boolean match = false;
    for (int i = 0; i < marked.length(); i++) {
      char c = marked.charAt(i);
      if (c == SearchRepository.START || c == SearchRepository.STOP) {
        if (!current.isEmpty()) segments.add(new Segment(current.toString(), match));
        current.setLength(0);
        match = c == SearchRepository.START;
      } else {
        current.append(c);
      }
    }
    if (!current.isEmpty()) segments.add(new Segment(current.toString(), match));
    return segments;
  }
}

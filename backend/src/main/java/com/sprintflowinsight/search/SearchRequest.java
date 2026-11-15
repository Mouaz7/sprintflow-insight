package com.sprintflowinsight.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Query parameters of {@code GET /api/search}.
 *
 * @param q free text or a task key such as {@code SF-123}
 * @param projectId restricts the search to one readable project; all readable projects when absent
 * @param types item kinds to include; all kinds when absent
 * @param limit page size, {@value #DEFAULT_LIMIT} by default
 * @param cursor opaque {@code nextCursor} from the previous page
 */
public record SearchRequest(
    @NotBlank @Size(max = MAX_QUERY_LENGTH) String q,
    @Positive Long projectId,
    @Size(max = 5) List<@Pattern(regexp = SearchType.WIRE_PATTERN) String> types,
    @Min(1) @Max(MAX_LIMIT) Integer limit,
    @Size(max = SearchCursor.MAX_LENGTH) @Pattern(regexp = "[A-Za-z0-9_-]+") String cursor) {
  public static final int MAX_QUERY_LENGTH = 200;
  public static final int MAX_LIMIT = 50;
  public static final int DEFAULT_LIMIT = 20;

  public int pageSize() {
    return limit == null ? DEFAULT_LIMIT : limit;
  }

  public Set<SearchType> typeSet() {
    if (types == null || types.isEmpty()) return EnumSet.allOf(SearchType.class);
    EnumSet<SearchType> selected = EnumSet.noneOf(SearchType.class);
    for (String type : types) selected.add(SearchType.fromWire(type));
    return selected;
  }
}

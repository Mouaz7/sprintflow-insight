package com.sprintflowinsight.search;

import java.util.List;

/**
 * One page of search results, best first.
 *
 * @param nextCursor pass as {@code cursor} for the next page; {@code null} on the last page
 */
public record SearchResponse(List<SearchHit> items, String nextCursor) {
  public static SearchResponse empty() {
    return new SearchResponse(List.of(), null);
  }
}

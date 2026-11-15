package com.sprintflowinsight.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprintflowinsight.search.SearchHit.Segment;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchSupportTest {
  private static final char START = SearchRepository.START;
  private static final char STOP = SearchRepository.STOP;

  @Test
  void splitsHeadlinesIntoMarkedSegments() {
    assertThat(SearchService.segments("Fix " + START + "login" + STOP + " redirect"))
        .containsExactly(
            new Segment("Fix ", false), new Segment("login", true), new Segment(" redirect", false));
    assertThat(SearchService.segments(START + "a" + STOP + START + "b" + STOP))
        .containsExactly(new Segment("a", true), new Segment("b", true));
    assertThat(SearchService.segments("plain")).containsExactly(new Segment("plain", false));
    assertThat(SearchService.segments("")).isEmpty();
    assertThat(SearchService.segments(null)).isEmpty();
    assertThat(SearchService.segments("x" + START + "unclosed"))
        .containsExactly(new Segment("x", false), new Segment("unclosed", true));
  }

  @Test
  void typesHaveStableWireNamesAndOrders() {
    for (SearchType type : SearchType.values()) {
      assertThat(SearchType.fromWire(type.wire())).isEqualTo(type);
      assertThat(SearchType.fromOrder(type.order())).isEqualTo(type);
      assertThat(type.wire()).matches(SearchType.WIRE_PATTERN);
    }
    assertThat(SearchType.TASK.order()).isEqualTo(5);
    assertThat(SearchType.PROJECT.order()).isEqualTo(1);
    assertThat(SearchType.fromWire(" Story ")).isEqualTo(SearchType.STORY);
    assertThatThrownBy(() -> SearchType.fromOrder(9)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void requestDefaultsToEveryTypeAndTwentyHits() {
    SearchRequest defaults = new SearchRequest("q", null, null, null, null);
    assertThat(defaults.pageSize()).isEqualTo(SearchRequest.DEFAULT_LIMIT);
    assertThat(defaults.typeSet()).isEqualTo(EnumSet.allOf(SearchType.class));
    assertThat(new SearchRequest("q", null, List.of(), 5, null).typeSet())
        .isEqualTo(EnumSet.allOf(SearchType.class));
    SearchRequest narrowed = new SearchRequest("q", null, List.of("task", "comment"), 5, null);
    assertThat(narrowed.pageSize()).isEqualTo(5);
    assertThat(narrowed.typeSet()).containsExactlyInAnyOrder(SearchType.TASK, SearchType.COMMENT);
  }
}

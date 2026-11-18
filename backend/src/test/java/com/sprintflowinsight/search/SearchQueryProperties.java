package com.sprintflowinsight.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprintflowinsight.common.error.ApiException;
import com.sprintflowinsight.workspace.task.TaskKey;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.constraints.UpperChars;

/**
 * Fuzzes the search parser and cursor with arbitrary text (the Jazzer harness follows in C7): the
 * generated tsquery is always well formed, bounded and free of user-supplied operators.
 */
class SearchQueryProperties {
  private static final String TERM = "'[\\p{L}\\p{N}]{1,64}':\\*";
  private static final String TSQUERY = "(" + TERM + "( & " + TERM + "){0,7})?";

  @Provide
  Arbitrary<String> hostileText() {
    return Arbitraries.oneOf(
        Arbitraries.strings().ofMaxLength(400),
        Arbitraries.strings().all().ofMaxLength(400),
        Arbitraries.strings().withChars("'\\&|!:*()<->% \"\t\nabcÅ1-").ofMaxLength(80));
  }

  @Property(tries = 2000)
  void anyTextYieldsAWellFormedBoundedQuery(@ForAll("hostileText") String text) {
    SearchQuery query = SearchQuery.parse(text);
    assertThat(query.tsquery()).matches(TSQUERY);
    assertThat(query.terms()).hasSizeLessThanOrEqualTo(SearchQuery.MAX_TERMS);
    assertThat(new HashSet<>(query.terms())).hasSameSizeAs(query.terms());
    assertThat(query.text()).doesNotContain("'", "&", "|", "!", ":", "*", "\\");
    assertThat(query.isEmpty()).isEqualTo(query.terms().isEmpty() && query.taskKey().isEmpty());
  }

  @Property(tries = 1000)
  void termsComeFromTheInputWords(@ForAll("hostileText") String text) {
    String folded = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    for (String term : SearchQuery.parse(text).terms()) assertThat(folded).contains(term);
  }

  @Property(tries = 500)
  void taskKeysAreRecognisedInAnyCase(
      @ForAll @UpperChars @StringLength(min = 2, max = 10) String project,
      @ForAll @IntRange(min = 1, max = 999_999_999) int number,
      @ForAll boolean lower) {
    String text = project + "-" + number;
    SearchQuery query = SearchQuery.parse(lower ? text.toLowerCase(Locale.ROOT) : text);
    assertThat(query.taskKey()).contains(new TaskKey(project, number));
    assertThat(query.terms())
        .containsExactly(project.toLowerCase(Locale.ROOT), Integer.toString(number));
  }

  @Property(tries = 500)
  void cursorsRoundTrip(
      @ForAll @IntRange(min = 0, max = 9_999_999) int micros,
      @ForAll @IntRange(min = 1, max = 5) int order,
      @ForAll @LongRange(min = 1) long id) {
    SearchCursor cursor = new SearchCursor(BigDecimal.valueOf(micros, 6), order, id);
    assertThat(SearchCursor.decode(cursor.encode())).isEqualTo(cursor);
  }

  @Property(tries = 1000)
  void arbitraryCursorTextFailsOnlyAsABadRequest(@ForAll("hostileText") String text) {
    try {
      SearchCursor cursor = SearchCursor.decode(text);
      assertThat(cursor.id()).isPositive();
      assertThat(cursor.typeOrder()).isBetween(1, 6);
    } catch (ApiException rejected) {
      assertThat(rejected.code().status().value()).isEqualTo(400);
    }
    assertThatThrownBy(() -> SearchCursor.decode(text + "\u0000")).isInstanceOf(ApiException.class);
  }
}

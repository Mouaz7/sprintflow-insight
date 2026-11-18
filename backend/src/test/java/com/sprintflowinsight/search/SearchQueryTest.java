package com.sprintflowinsight.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprintflowinsight.workspace.task.TaskKey;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SearchQueryTest {
  @Test
  void splitsLowerCasesAndDeduplicatesWords() {
    SearchQuery query = SearchQuery.parse("  Login BUG, login!  sprint-planering ");
    assertThat(query.terms()).containsExactly("login", "bug", "sprint", "planering");
    assertThat(query.tsquery())
        .isEqualTo("'login':* & 'bug':* & 'sprint':* & 'planering':*");
    assertThat(query.text()).isEqualTo("login bug sprint planering");
    assertThat(query.taskKey()).isEmpty();
    assertThat(query.isEmpty()).isFalse();
  }

  @Test
  void keepsLettersOfEveryScriptAndFoldsCompatibilityForms() {
    assertThat(SearchQuery.parse("Åtgärda Ölstugan ＡＢＣ x²").terms())
        .containsExactly("åtgärda", "ölstugan", "abc", "x2");
  }

  @Test
  void neverPassesTsquerySyntaxThrough() {
    SearchQuery query = SearchQuery.parse("a' | b:* & !c (d) <-> e\\");
    assertThat(query.terms()).containsExactly("a", "b", "c", "d", "e");
    assertThat(query.tsquery()).isEqualTo("'a':* & 'b':* & 'c':* & 'd':* & 'e':*");
  }

  @Test
  void punctuationOnlyOrNullIsEmpty() {
    assertThat(SearchQuery.parse("'&|!:*()").isEmpty()).isTrue();
    assertThat(SearchQuery.parse(null).isEmpty()).isTrue();
    assertThat(SearchQuery.parse("").tsquery()).isEmpty();
  }

  @Test
  void limitsTheNumberAndLengthOfTerms() {
    SearchQuery many = SearchQuery.parse("a b c d e f g h i j k");
    assertThat(many.terms()).hasSize(SearchQuery.MAX_TERMS).endsWith("h");

    SearchQuery longWord = SearchQuery.parse("x".repeat(100));
    assertThat(longWord.terms()).containsExactly("x".repeat(SearchQuery.MAX_TERM_LENGTH));

    String astral = "𝐀".repeat(70); // MATHEMATICAL BOLD CAPITAL A, NFKC -> 'a'
    assertThat(SearchQuery.parse(astral).terms().getFirst()).hasSize(SearchQuery.MAX_TERM_LENGTH);
    String emojiLetters = "𐐀".repeat(70); // DESERET CAPITAL LONG I, a letter
    String term = SearchQuery.parse(emojiLetters).terms().getFirst();
    assertThat(term.codePointCount(0, term.length())).isEqualTo(SearchQuery.MAX_TERM_LENGTH);
    assertThat(Character.isHighSurrogate(term.charAt(term.length() - 1))).isFalse();
  }

  @Test
  void recognisesATaskKeyOnlyWhenItIsTheWholeQuery() {
    SearchQuery key = SearchQuery.parse(" sf-123 ");
    assertThat(key.taskKey()).contains(new TaskKey("SF", 123));
    assertThat(key.terms()).containsExactly("sf", "123");
    assertThat(SearchQuery.parse("SF-123 login").taskKey()).isEmpty();
    assertThat(SearchQuery.parse("SF-0").taskKey()).isEmpty();
  }

  @Test
  void rejectsTermsThatCouldBreakTheQuery() {
    assertThatThrownBy(() -> new SearchQuery(List.of("a'b"), Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SearchQuery(List.of(""), Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new SearchQuery(
                    List.of("a", "b", "c", "d", "e", "f", "g", "h", "i"), Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new SearchQuery(List.of("ok"), Optional.empty()).terms()).containsExactly("ok");
  }
}

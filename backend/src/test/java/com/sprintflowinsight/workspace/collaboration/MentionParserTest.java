package com.sprintflowinsight.workspace.collaboration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MentionParserTest {
  @ParameterizedTest
  @CsvSource(
      value = {
        "Thanks @Anna.|anna",
        "(@anna@example.test), thanks!|anna@example.test",
        "x@y.test|NONE",
        "@@anna|NONE",
        "`@anna` @bob|bob",
        "`unclosed @anna|anna",
        "@A @a @b|a,b",
        "@first.last+tag@example.test|first.last+tag@example.test",
        "plain text|NONE"
      },
      delimiter = '|')
  void scansBoundariesCodeAndDistinctAddresses(String input, String expected) {
    assertThat(MentionParser.parse(input))
        .containsExactlyElementsOf(
            expected.equals("NONE") ? List.of() : List.of(expected.split(",")));
  }

  @Test
  void boundsWorkAndHandlesNull() {
    assertThat(MentionParser.parse(null)).isEmpty();
    assertThat(MentionParser.parse("")).isEmpty();
    assertThat(MentionParser.parse("@" + "a".repeat(255))).isEmpty();
    String input =
        java.util.stream.IntStream.range(0, 30)
            .mapToObj(i -> "@user" + i)
            .collect(java.util.stream.Collectors.joining(" "));
    assertThat(MentionParser.parse(input)).hasSize(20);
  }

  @Test
  void resolvesOnlyMembersAndRequiresFullEmailForAmbiguousNames() {
    var members =
        List.of(
            new MentionResolver.Candidate(1L, "Anna@a.test"),
            new MentionResolver.Candidate(2L, "anna@b.test"),
            new MentionResolver.Candidate(3L, "Bob@b.test"),
            new MentionResolver.Candidate(null, "unknown@test"));
    assertThat(
            MentionResolver.resolve(
                List.of("anna", "ANNA@A.TEST", "bob", "outside@test", "bob"), members))
        .containsExactly(1L, 3L);
    assertThat(MentionResolver.resolve(List.of(), members)).isEmpty();
    assertThat(MentionResolver.resolve(List.of("anna"), List.of())).isEmpty();
    assertThat(MentionService.added(List.of(1L), List.of(1L, 2L, 3L, 3L), 2L)).containsExactly(3L);
  }
}

package com.sprintflowinsight.workspace.poker;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PokerStatisticsTest {
  @Test
  void builtInDecksAreFixedAndDoNotAcceptCustomCards() {
    assertThat(PokerDeck.FIBONACCI.cards(null))
        .containsExactly("1", "2", "3", "5", "8", "13", "?", "☕");
    assertThat(PokerDeck.TSHIRT.cards(List.of()))
        .containsExactly("XS", "S", "M", "L", "XL", "?", "☕");
    assertThatThrownBy(() -> PokerDeck.TSHIRT.cards(List.of("1")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PokerDeck.FIBONACCI.cards(null).add("21"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  static List<List<String>> invalidDecks() {
    return Arrays.asList(
        null,
        List.of(),
        List.of("1"),
        List.of("?", "☕"),
        List.of("1", "1"),
        List.of("1", " 1 "),
        List.of("1", "<img>"),
        List.of("1", "a b"),
        List.of("1", "123456789"),
        Arrays.asList("1", null),
        java.util.stream.IntStream.range(0, 21).mapToObj(String::valueOf).toList());
  }

  @ParameterizedTest
  @MethodSource("invalidDecks")
  void rejectsInvalidCustomDecks(List<String> cards) {
    assertThatThrownBy(() -> PokerDeck.CUSTOM.cards(cards))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void customDeckPreservesMeaningfulOrderAndStripsEdges() {
    assertThat(PokerDeck.CUSTOM.cards(List.of(" XS ", "M", "½", "☕")))
        .containsExactly("XS", "M", "½", "☕");
  }

  @Test
  void abstentionsNeverBecomeAnEstimateAndUnknownCardsAreRejected() {
    var stats = PokerStatistics.of(PokerDeck.FIBONACCI.cards(null), List.of("?", "☕"));
    assertThat(stats.votes()).isEqualTo(2);
    assertThat(stats.abstentions()).isEqualTo(2);
    assertThat(stats.consensus()).isFalse();
    assertThat(stats.suggestion()).isNull();
    assertThat(stats.suggestedStoryPoints()).isNull();
    assertThat(stats.mean()).isNull();
    assertThat(stats.min()).isNull();
    assertThat(stats.max()).isNull();
    assertThat(stats.median()).isNull();
    assertThat(stats.modes()).isEmpty();
    assertThat(stats.agreement()).isZero();
    assertThat(stats.revoteRecommended()).isTrue();
    assertThat(PokerStatistics.of(PokerDeck.FIBONACCI.cards(null), List.of()).votes()).isZero();
    assertThatThrownBy(() -> PokerStatistics.of(List.of("1", "2"), List.of("3")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void numericalStatisticsHaveIndependentExpectedValues() {
    var stats =
        PokerStatistics.of(PokerDeck.FIBONACCI.cards(null), List.of("8", "2", "2", "5", "?", "☕"));
    assertThat(stats.distribution())
        .containsExactly(
            new PokerStatistics.CardCount("2", 2),
            new PokerStatistics.CardCount("5", 1),
            new PokerStatistics.CardCount("8", 1),
            new PokerStatistics.CardCount("?", 1),
            new PokerStatistics.CardCount("☕", 1));
    assertThat(stats.min()).isEqualTo("2");
    assertThat(stats.max()).isEqualTo("8");
    assertThat(stats.median()).isEqualTo("2");
    assertThat(stats.modes()).containsExactly("2");
    assertThat(stats.mean()).isEqualTo(4.25);
    assertThat(stats.agreement()).isEqualTo(0.5);
    assertThat(stats.consensus()).isFalse();
    assertThat(stats.spread()).isEqualTo(3);
    assertThat(stats.revoteRecommended()).isTrue();
    assertThat(stats.suggestedStoryPoints()).isEqualTo(2);
  }

  @Test
  void tShirtsAndCustomCardsUseDeckOrderAndNeverInventAPointMapping() {
    var shirts = PokerStatistics.of(PokerDeck.TSHIRT.cards(null), List.of("L", "M", "?"));
    assertThat(shirts.median()).isEqualTo("M");
    assertThat(shirts.modes()).containsExactly("M", "L");
    assertThat(shirts.mean()).isNull();
    assertThat(shirts.suggestedStoryPoints()).isNull();
    assertThat(shirts.revoteRecommended()).isFalse();
    var custom =
        PokerStatistics.of(List.of("small", "big", "huge"), List.of("huge", "huge", "huge"));
    assertThat(custom.consensus()).isTrue();
    assertThat(custom.agreement()).isEqualTo(1);
    assertThat(custom.spread()).isZero();
    assertThat(custom.min()).isEqualTo("huge");
    assertThat(custom.max()).isEqualTo("huge");
    assertThat(custom.median()).isEqualTo("huge");
    assertThat(custom.revoteRecommended()).isFalse();
  }

  @Test
  void numericMeanAndAllowedStoryPointsDoNotOverflowOrRound() {
    assertThat(PokerStatistics.numeric("½")).isEqualTo(0.5);
    assertThat(PokerStatistics.numeric("999999.99")).isEqualTo(999999.99);
    assertThat(PokerStatistics.numeric(null)).isNull();
    for (String card : List.of("NaN", "Infinity", "1e100", "-1", "1000000", "3.1415"))
      assertThat(PokerStatistics.numeric(card)).isNull();
    for (String card : List.of("13", "½", "1.1", "0", "XL"))
      assertThat(PokerStatistics.storyPoints(card)).isNull();
    assertThat(PokerStatistics.storyPoints("5.00")).isEqualTo(5);
    assertThat(PokerStatistics.of(List.of("½", "1", "2"), List.of("½", "1", "2")).mean())
        .isCloseTo(7.0 / 6, within(0.00001));
  }

  @Test
  void converterKeepsOrderSymbolsAndImmutableSnapshots() {
    var converter = new CardsConverter();
    var cards = List.of("XS", "½", "?", "☕");
    assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(cards)))
        .isEqualTo(cards);
    assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("[]");
    assertThatThrownBy(() -> converter.convertToEntityAttribute("broken"))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void revealedRoundStaysFixedWhileTheNextRoundVotes() {
    var at = Instant.parse("2026-11-18T12:00:00Z");
    var session = new PokerSession(1L, 2L, 3L, PokerDeck.TSHIRT, PokerDeck.TSHIRT.cards(null), at);
    assertThat(session.getStatus()).isEqualTo(PokerSession.Status.VOTING);
    assertThat(session.getRevealedRound()).isZero();
    session.reveal(at.plusSeconds(1));
    session.revote(at.plusSeconds(2));
    assertThat(session.getRound()).isEqualTo(2);
    assertThat(session.getRevealedRound()).isEqualTo(1);
    assertThat(session.getClosedAt()).isNull();
    session.reveal(at.plusSeconds(3));
    session.accept("M", 3, at.plusSeconds(4));
    assertThat(session.getStatus().open()).isFalse();
    assertThat(session.getFinalStoryPoints()).isEqualTo(3);
    assertThat(session.getFinalValue()).isEqualTo("M");
    assertThat(session.getClosedAt()).isEqualTo(at.plusSeconds(4));
    assertThat(session.getUpdatedAt()).isEqualTo(at.plusSeconds(4));
    var cancelled = new PokerSession(1L, 2L, 3L, PokerDeck.CUSTOM, List.of("1", "3"), at);
    cancelled.cancel(at.plusSeconds(1));
    assertThat(cancelled.getStatus()).isEqualTo(PokerSession.Status.CANCELLED);
    assertThat(cancelled.getClosedAt()).isNotNull();
  }
}

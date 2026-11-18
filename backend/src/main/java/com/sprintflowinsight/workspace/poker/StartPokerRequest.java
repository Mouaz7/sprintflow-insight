package com.sprintflowinsight.workspace.poker;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Starts a session for a story of the project.
 *
 * @param cards only for {@link PokerDeck#CUSTOM}: 2 to 20 distinct cards in deck order
 */
public record StartPokerRequest(
    @NotNull(message = "Story is required.") Long storyId,
    @NotNull(message = "Deck is required.") PokerDeck deck,
    @Size(max = 20, message = "A custom deck can have at most 20 cards.")
        List<
                @NotNull(message = "Cards cannot be empty.")
                @Size(max = 8, message = "Each card can be at most 8 characters.") String>
            cards) {}

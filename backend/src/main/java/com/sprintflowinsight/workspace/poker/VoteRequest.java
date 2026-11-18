package com.sprintflowinsight.workspace.poker;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The caller's card for the current round. It must be one of the session's cards. */
public record VoteRequest(
    @jakarta.validation.constraints.NotNull
        @jakarta.validation.constraints.Min(1)
        @jakarta.validation.constraints.Max(50)
        Integer round,
    @NotBlank(message = "Choose a card.") @Size(max = 8, message = "Choose a card from the deck.")
        String card) {}

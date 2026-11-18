package com.sprintflowinsight.workspace.poker;

import jakarta.validation.constraints.NotNull;

/**
 * Closes a revealed session and writes the agreed estimate to the story.
 *
 * @param storyPoints 1, 2, 3, 5 or 8; usually the round's suggestion, but the team decides
 */
public record AcceptPokerRequest(
    @NotNull @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(50)
        Integer round,
    @NotNull(message = "Story points are required.") Integer storyPoints,
    @NotNull @jakarta.validation.constraints.PositiveOrZero Long storyVersion) {}

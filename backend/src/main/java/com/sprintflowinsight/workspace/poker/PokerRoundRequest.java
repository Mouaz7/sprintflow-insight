package com.sprintflowinsight.workspace.poker;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** The round the caller saw; delayed commands must never act on a later round. */
public record PokerRoundRequest(@NotNull @Min(1) @Max(50) Integer round) {}

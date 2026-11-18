package com.sprintflowinsight.workspace.poker;

/** List entries deliberately contain no votes, histories or caller-specific values. */
public record PokerSessionSummary(
    Long id,
    Long storyId,
    String storyTitle,
    PokerSession.Status status,
    int round,
    Integer finalStoryPoints) {}

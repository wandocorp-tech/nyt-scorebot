package com.wandocorp.nytscorebot.service.scoreboard;

/**
 * One label/leftValue/rightValue triple to be appended below a comparison scoreboard.
 *
 * <p>Used by {@link GameComparisonScoreboard#extraRowsBelowOutcome} to render the
 * crossword {@code avg} / {@code pb} rows. Cells are rendered verbatim — callers
 * are responsible for any placeholder substitution (e.g. {@code "-"}).
 */
public record ExtraRow(String label, String leftValue, String rightValue) {}

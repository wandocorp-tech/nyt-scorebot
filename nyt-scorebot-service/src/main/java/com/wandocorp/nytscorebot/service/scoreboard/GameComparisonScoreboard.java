package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import java.util.List;

public interface GameComparisonScoreboard {
    String gameType();
    boolean hasResult(Scoreboard scoreboard);
    String header(Scoreboard scoreboard);
    String scoreLabel(Scoreboard scoreboard);
    List<String> emojiGridRows(Scoreboard scoreboard);
    ComparisonOutcome determineOutcome(Scoreboard s1, String name1, Scoreboard s2, String name2);
    int leadingSpaces();
    int baseGap();
    int maxEmojisPerRow();
    default boolean usesStreakDisplay() { return false; }
    default boolean usesScoreLabelRow() { return false; }
    default String flagsRow(Scoreboard scoreboard) { return ""; }

    /** True for Mini/Midi/Main crosswords — gates the inline PB/Δavg rows. */
    default boolean isCrossword() { return false; }

    /**
     * Returns today's total seconds for the rendered crossword result, used to compute
     * the {@code Δ avg} value. Default is empty for non-crossword games.
     */
    default java.util.OptionalInt todaySeconds(Scoreboard scoreboard) { return java.util.OptionalInt.empty(); }
}

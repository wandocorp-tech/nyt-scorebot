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
}

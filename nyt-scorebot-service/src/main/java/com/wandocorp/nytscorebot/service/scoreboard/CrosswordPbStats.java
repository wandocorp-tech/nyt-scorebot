package com.wandocorp.nytscorebot.service.scoreboard;

import java.util.OptionalInt;

/**
 * Per-(player, game) prior-clean average and current PB seconds, used by the renderer
 * to draw the inline {@code Δ avg} / PB rows on crossword scoreboards.
 *
 * <p>Either field may be empty independently, but in practice both are present together
 * (a player has either history or none).
 */
public record CrosswordPbStats(OptionalInt avgSeconds, OptionalInt pbSeconds) {
    public static final CrosswordPbStats EMPTY =
            new CrosswordPbStats(OptionalInt.empty(), OptionalInt.empty());

    public boolean hasHistory() {
        return avgSeconds.isPresent() || pbSeconds.isPresent();
    }
}

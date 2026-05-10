package com.wandocorp.nytscorebot.service.stats;

import com.wandocorp.nytscorebot.model.GameType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Immutable report produced by {@link CrosswordStatsService} for a given window and game filter.
 *
 * <p>When no scoreboard data exists for the window, {@code games} is empty and callers should
 * render an empty-period placeholder instead of per-game blocks.
 */
public record CrosswordStatsReport(
        GameTypeFilter filter,
        LocalDate from,
        LocalDate to,
        String player1Name,
        String player2Name,
        List<GameStats> games
) {
    public record GameStats(
            GameType gameType,
            List<UserGameStats> players,
            Optional<DowBlock> dowBlock
    ) {}

    public record UserGameStats(
            String playerName,
            int wins,
            int gamesPlayed,
            OptionalDouble avgSeconds,
            OptionalInt bestSeconds,
            Optional<LocalDate> bestDate,
            int excludedAssistedCount
    ) {
        /** Convenience constructor for callers that don't track exclusions (e.g. Mini/Midi tests). */
        public UserGameStats(String playerName, int wins, int gamesPlayed,
                             OptionalDouble avgSeconds, OptionalInt bestSeconds,
                             Optional<LocalDate> bestDate) {
            this(playerName, wins, gamesPlayed, avgSeconds, bestSeconds, bestDate, 0);
        }
    }

    public record DowBlock(List<DowRow> rows) {}

    public record DowRow(
            DayOfWeek dayOfWeek,
            Optional<DowCell> player1Cell,
            Optional<DowCell> player2Cell
    ) {}

    public record DowCell(double avgSeconds, int count) {}

    /** Map keyed by player name (for easy lookup from {@link DowRow}). */
    public record DowStats(Map<String, Map<DayOfWeek, DowCell>> cellsByPlayer) {}
}

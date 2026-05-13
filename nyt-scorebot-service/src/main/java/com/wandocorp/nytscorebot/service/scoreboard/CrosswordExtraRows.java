package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.service.history.CrosswordHistoryStats;

import java.util.OptionalInt;

/**
 * Helpers for rendering the crossword {@code avg} / {@code pb} rows shared by
 * Mini, Midi, and Main scoreboards.
 */
final class CrosswordExtraRows {

    private CrosswordExtraRows() {}

    /**
     * Build a single {@link ExtraRow} from a label and the corresponding {@link OptionalInt}
     * cells for each player. Empty values are rendered using
     * {@link BotText#SCOREBOARD_EMPTY_CELL}.
     */
    static ExtraRow row(String label, OptionalInt left, OptionalInt right) {
        return new ExtraRow(label, format(left), format(right));
    }

    static String format(OptionalInt seconds) {
        if (seconds.isEmpty()) return BotText.SCOREBOARD_EMPTY_CELL;
        int total = seconds.getAsInt();
        int hours = total / 3600;
        int minutes = (total % 3600) / 60;
        int secs = total % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, secs)
                : String.format("%d:%02d", minutes, secs);
    }

    /**
     * Build the standard avg + pb extra rows from two {@link CrosswordHistoryStats}.
     */
    static java.util.List<ExtraRow> avgPbRows(CrosswordHistoryStats left, CrosswordHistoryStats right) {
        return java.util.List.of(
                row(BotText.SCOREBOARD_AVG_LABEL, left.avgSeconds(), right.avgSeconds()),
                row(BotText.SCOREBOARD_PB_LABEL,  left.pbSeconds(),  right.pbSeconds())
        );
    }
}

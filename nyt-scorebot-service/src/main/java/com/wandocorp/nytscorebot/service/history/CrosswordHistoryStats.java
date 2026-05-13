package com.wandocorp.nytscorebot.service.history;

import java.util.OptionalInt;

/**
 * Read-side view of one bucket in {@code crossword_history_stats}.
 *
 * <p>{@code avgSeconds} is empty when the bucket has zero qualifying samples
 * (e.g. a seed-only row from the V8 manual PB seed). {@code pbSeconds} is
 * empty when no PB has ever been recorded for that bucket.
 */
public record CrosswordHistoryStats(OptionalInt avgSeconds, OptionalInt pbSeconds) {

    public static final CrosswordHistoryStats EMPTY =
            new CrosswordHistoryStats(OptionalInt.empty(), OptionalInt.empty());
}

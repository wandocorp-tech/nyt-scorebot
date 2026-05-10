package com.wandocorp.nytscorebot.model;

import java.time.DayOfWeek;
import java.util.Optional;

/**
 * Maps {@link Optional}&lt;{@link DayOfWeek}&gt; to a non-null sentinel string for use as a
 * {@code personal_best.day_of_week} column value.
 *
 * <p>Background: H2 and Postgres treat NULL as distinct in unique constraints — the opposite
 * of what the {@code (user_id, game_type, day_of_week)} composite uniqueness needs for Mini/Midi
 * rows that have no DoW partition. Using a non-null sentinel value sidesteps that semantics gap.
 *
 * <p>Sentinel: {@value #ALL_DAYS_SENTINEL}.
 */
public final class PbDayOfWeek {

    public static final String ALL_DAYS_SENTINEL = "__ALL__";

    private PbDayOfWeek() {}

    /**
     * Encode an {@link Optional}&lt;{@link DayOfWeek}&gt; to its column value:
     * present → {@link DayOfWeek#name()}, empty → {@link #ALL_DAYS_SENTINEL}.
     */
    public static String encode(Optional<DayOfWeek> dow) {
        return dow.map(Enum::name).orElse(ALL_DAYS_SENTINEL);
    }

    /**
     * Decode a column value back to an {@link Optional}&lt;{@link DayOfWeek}&gt;.
     * The sentinel value decodes to {@link Optional#empty()}.
     *
     * @throws IllegalArgumentException if {@code raw} is null or not a recognised value.
     */
    public static Optional<DayOfWeek> decode(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("day_of_week column value must not be null");
        }
        if (ALL_DAYS_SENTINEL.equals(raw)) {
            return Optional.empty();
        }
        return Optional.of(DayOfWeek.valueOf(raw));
    }
}

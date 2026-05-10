package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.model.GameType;

import java.time.DayOfWeek;
import java.util.Optional;

/**
 * Outcome of {@link PersonalBestService#recompute(com.wandocorp.nytscorebot.entity.User, GameType, java.time.LocalDate, int, boolean)}.
 *
 * <p>Sealed type:
 * <ul>
 *     <li>{@link NoChange} — no PB row was inserted or updated (not clean, slower, equal, or seeded).</li>
 *     <li>{@link NewPb} — a new personal best was set; the listener should announce it.</li>
 * </ul>
 */
public sealed interface PbUpdateOutcome permits PbUpdateOutcome.NoChange, PbUpdateOutcome.NewPb {

    /** Singleton no-change marker. */
    NoChange NO_CHANGE = new NoChange();

    final class NoChange implements PbUpdateOutcome {
        private NoChange() {}
    }

    /**
     * A new PB was recorded.
     *
     * @param priorSeconds the previous PB time, or {@code null} for first-ever PB
     * @param newSeconds   the new PB time
     * @param gameType     the crossword variant
     * @param dayOfWeek    populated for Main, empty for Mini/Midi
     */
    record NewPb(Integer priorSeconds, int newSeconds, GameType gameType, Optional<DayOfWeek> dayOfWeek)
            implements PbUpdateOutcome {
    }
}

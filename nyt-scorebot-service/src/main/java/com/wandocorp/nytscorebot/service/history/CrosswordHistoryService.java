package com.wandocorp.nytscorebot.service.history;

import com.wandocorp.nytscorebot.entity.CrosswordHistoryStat;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.repository.CrosswordHistoryStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Read + write owner of {@code crossword_history_stats}.
 *
 * <p>Encapsulates the qualifying rule for Main (clean solves only — no check,
 * no lookups, no duo) and the bucket-key derivation (Mini/Midi use sentinel
 * day_of_week 0; Main uses Java {@code DayOfWeek.getValue()} 1..7).
 */
@Service
@RequiredArgsConstructor
public class CrosswordHistoryService {

    /** Sentinel day_of_week value for Mini and Midi (one bucket per user/game). */
    static final byte NO_WEEKDAY = 0;

    private final CrosswordHistoryStatRepository repository;

    /**
     * Look up the stored stats for a player's bucket.
     *
     * @param weekday must be present for {@link CrosswordGame#MAIN} and empty for
     *                {@link CrosswordGame#MINI}/{@link CrosswordGame#MIDI}.
     */
    public CrosswordHistoryStats getStats(User user, CrosswordGame game, Optional<DayOfWeek> weekday) {
        validateWeekdayArg(game, weekday);
        byte dow = bucketKey(game, weekday);
        return repository.findByUserIdAndGameTypeAndDayOfWeek(user.getId(), game.name(), dow)
                .map(CrosswordHistoryService::toStats)
                .orElse(CrosswordHistoryStats.EMPTY);
    }

    /**
     * Record a freshly-saved crossword submission. For Main, non-clean submissions
     * (any of {@code checkUsed}, {@code lookups > 0}, {@code duo}) are no-ops.
     *
     * <p>Caller is responsible for invoking this inside the same transaction as the
     * underlying {@code Scoreboard.save(...)}.
     */
    @Transactional
    public void recordSubmission(User user, CrosswordGame game, LocalDate date, int totalSeconds,
                                 boolean checkUsed, int lookups, boolean duo) {
        if (game == CrosswordGame.MAIN && (checkUsed || lookups > 0 || duo)) {
            return;
        }
        byte dow = (game == CrosswordGame.MAIN)
                ? (byte) date.getDayOfWeek().getValue()
                : NO_WEEKDAY;
        repository.upsert(user.getId(), game.name(), dow, totalSeconds);
    }

    private static byte bucketKey(CrosswordGame game, Optional<DayOfWeek> weekday) {
        return game == CrosswordGame.MAIN
                ? (byte) weekday.orElseThrow().getValue()
                : NO_WEEKDAY;
    }

    private static void validateWeekdayArg(CrosswordGame game, Optional<DayOfWeek> weekday) {
        if (game == CrosswordGame.MAIN && weekday.isEmpty()) {
            throw new IllegalArgumentException("MAIN requires a weekday");
        }
        if (game != CrosswordGame.MAIN && weekday.isPresent()) {
            throw new IllegalArgumentException(game + " must not be queried with a weekday");
        }
    }

    private static CrosswordHistoryStats toStats(CrosswordHistoryStat row) {
        OptionalInt avg = row.getSampleCount() > 0
                ? OptionalInt.of((int) Math.round((double) row.getSumSeconds() / row.getSampleCount()))
                : OptionalInt.empty();
        OptionalInt pb = row.getPbSeconds() != null
                ? OptionalInt.of(row.getPbSeconds())
                : OptionalInt.empty();
        return new CrosswordHistoryStats(avg, pb);
    }
}

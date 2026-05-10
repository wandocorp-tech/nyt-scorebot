package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.entity.PersonalBest;
import com.wandocorp.nytscorebot.entity.PersonalBestSource;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.model.PbDayOfWeek;
import com.wandocorp.nytscorebot.repository.PersonalBestRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Maintains per-user crossword personal best records.
 *
 * <p>Manual-vs-computed precedence rule: a {@link PersonalBestSource#MANUAL} row is preserved
 * against any clean save that is equal or slower; a strictly faster clean save replaces it
 * (and transitions the source to {@link PersonalBestSource#COMPUTED}). Manual rows are seeded
 * via direct SQL — see {@code docs/} or the {@code personal_best} migration; the bot never
 * inserts manual rows itself.
 *
 * <p>Assisted Main results (duo / lookups / check) never qualify as a PB; the call site is
 * expected to determine cleanness via {@link com.wandocorp.nytscorebot.model.MainCrosswordResult#isAssisted()}.
 */
@Service
public class PersonalBestService {

    private final PersonalBestRepository repository;

    public PersonalBestService(PersonalBestRepository repository) {
        this.repository = repository;
    }

    /**
     * Recompute the PB for {@code user} / {@code gameType} given today's clean-or-not result.
     *
     * @param user         the player
     * @param gameType     MINI_CROSSWORD, MIDI_CROSSWORD, or MAIN_CROSSWORD
     * @param puzzleDate   the puzzle date (used both as the DoW source for Main and as bestDate)
     * @param totalSeconds the player's solve time
     * @param isClean      whether the result was unassisted (duo/check/lookups all false/zero)
     * @return {@link PbUpdateOutcome.NewPb} when a new PB was set; otherwise {@link PbUpdateOutcome#NO_CHANGE}.
     */
    public PbUpdateOutcome recompute(User user, GameType gameType, LocalDate puzzleDate,
                                     int totalSeconds, boolean isClean) {
        if (!isClean) {
            return PbUpdateOutcome.NO_CHANGE;
        }
        Optional<DayOfWeek> dow = scopeFor(gameType, puzzleDate);
        String dowKey = PbDayOfWeek.encode(dow);
        Optional<PersonalBest> existing = repository.findByUserAndGameTypeAndDayOfWeek(user, gameType, dowKey);

        if (existing.isEmpty()) {
            PersonalBest row = new PersonalBest(user, gameType, dowKey, totalSeconds, puzzleDate,
                    PersonalBestSource.COMPUTED);
            repository.save(row);
            return new PbUpdateOutcome.NewPb(null, totalSeconds, gameType, dow);
        }

        PersonalBest pb = existing.get();
        if (totalSeconds >= pb.getBestSeconds()) {
            return PbUpdateOutcome.NO_CHANGE;
        }
        int prior = pb.getBestSeconds();
        pb.setBestSeconds(totalSeconds);
        pb.setBestDate(puzzleDate);
        pb.setSource(PersonalBestSource.COMPUTED);
        repository.save(pb);
        return new PbUpdateOutcome.NewPb(prior, totalSeconds, gameType, dow);
    }

    /**
     * Seed a PB row from historical data without producing a {@link PbUpdateOutcome.NewPb}
     * (so the backfill never triggers a celebratory message). Always returns
     * {@link PbUpdateOutcome#NO_CHANGE}.
     *
     * <p>If a row already exists for this scope, it is left untouched (manual rows are
     * preserved; previously-seeded computed rows are not double-seeded).
     */
    public PbUpdateOutcome seedFromHistory(User user, GameType gameType, Optional<DayOfWeek> dayOfWeek,
                                           int seconds, LocalDate date) {
        validateScope(gameType, dayOfWeek);
        String dowKey = PbDayOfWeek.encode(dayOfWeek);
        if (repository.findByUserAndGameTypeAndDayOfWeek(user, gameType, dowKey).isPresent()) {
            return PbUpdateOutcome.NO_CHANGE;
        }
        repository.save(new PersonalBest(user, gameType, dowKey, seconds, date, PersonalBestSource.COMPUTED));
        return PbUpdateOutcome.NO_CHANGE;
    }

    private static Optional<DayOfWeek> scopeFor(GameType gameType, LocalDate puzzleDate) {
        return switch (gameType) {
            case MAIN_CROSSWORD -> Optional.of(puzzleDate.getDayOfWeek());
            case MINI_CROSSWORD, MIDI_CROSSWORD -> Optional.empty();
            default -> throw new IllegalArgumentException(
                    "PersonalBestService only handles crossword game types; got: " + gameType);
        };
    }

    private static void validateScope(GameType gameType, Optional<DayOfWeek> dayOfWeek) {
        boolean expectsDow = gameType == GameType.MAIN_CROSSWORD;
        if (expectsDow != dayOfWeek.isPresent()) {
            throw new IllegalArgumentException(
                    "Scope mismatch: gameType=" + gameType + " dayOfWeek=" + dayOfWeek);
        }
    }
}

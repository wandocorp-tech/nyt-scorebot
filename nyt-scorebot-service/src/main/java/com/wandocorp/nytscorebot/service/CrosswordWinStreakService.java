package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.model.CrosswordResult;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.model.MainCrosswordResult;
import com.wandocorp.nytscorebot.service.scoreboard.ComparisonOutcome;
import com.wandocorp.nytscorebot.service.scoreboard.MainCrosswordScoreboard;
import com.wandocorp.nytscorebot.service.scoreboard.MidiCrosswordScoreboard;
import com.wandocorp.nytscorebot.service.scoreboard.MiniCrosswordScoreboard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Adapter that consumes the existing crossword {@code Scoreboard} comparison
 * logic and forwards the resulting {@link ComparisonOutcome} to
 * {@link WinStreakService}.
 *
 * <p>Each crossword game is handled independently — a missing game on one
 * scoreboard produces a {@code WaitingFor} outcome (no change) for that game
 * only, without blocking updates for the other crossword games.
 */
@RequiredArgsConstructor
@Service
public class CrosswordWinStreakService {

    /** The three crossword games, in board display order. */
    public static final List<GameType> CROSSWORDS =
            List.of(GameType.MINI_CROSSWORD, GameType.MIDI_CROSSWORD, GameType.MAIN_CROSSWORD);

    private final WinStreakService winStreakService;
    private final MiniCrosswordScoreboard miniScoreboard;
    private final MidiCrosswordScoreboard midiScoreboard;
    private final MainCrosswordScoreboard mainScoreboard;

    /**
     * Compute and apply win streak updates for all three crosswords.
     *
     * @return the {@link ComparisonOutcome} computed for each crossword game, keyed by game type.
     *         Returns an empty map when either scoreboard is absent. The outcomes are returned so
     *         downstream consumers (such as triple crown detection) can reuse the comparison result
     *         rather than re-deriving winners from raw solve times.
     */
    public Map<GameType, ComparisonOutcome> updateAll(Scoreboard sb1, String name1,
                                                      Scoreboard sb2, String name2, LocalDate date) {
        if (sb1 == null || sb2 == null) return Map.of();
        Map<GameType, ComparisonOutcome> outcomes = new EnumMap<>(GameType.class);
        for (GameType gameType : CROSSWORDS) {
            outcomes.put(gameType, updateOne(gameType, sb1, name1, sb2, name2, date));
        }
        return outcomes;
    }

    /**
     * Compute and apply win streak update for a single crossword game.
     *
     * @return the computed outcome, or empty when either scoreboard is absent or the game
     *         is not a crossword.
     */
    public Optional<ComparisonOutcome> updateGame(GameType gameType, Scoreboard sb1, String name1,
                                                  Scoreboard sb2, String name2, LocalDate date) {
        if (sb1 == null || sb2 == null) return Optional.empty();
        if (!WinStreakService.isCrossword(gameType)) return Optional.empty();
        return Optional.of(updateOne(gameType, sb1, name1, sb2, name2, date));
    }

    /**
     * Computes the {@link ComparisonOutcome} for each crossword <em>without</em> applying any
     * win streak change. Used when only one game's streak should be mutated (a flag change)
     * but the full picture is still needed — for example to re-evaluate the triple crown.
     *
     * @return outcomes keyed by game type, or an empty map when either scoreboard is absent
     */
    public Map<GameType, ComparisonOutcome> computeOutcomes(Scoreboard sb1, String name1,
                                                            Scoreboard sb2, String name2) {
        if (sb1 == null || sb2 == null) return Map.of();
        Map<GameType, ComparisonOutcome> outcomes = new EnumMap<>(GameType.class);
        for (GameType gameType : CROSSWORDS) {
            outcomes.put(gameType, computeOne(gameType, sb1, name1, sb2, name2));
        }
        return outcomes;
    }

    private ComparisonOutcome updateOne(GameType gameType, Scoreboard sb1, String name1,
                                        Scoreboard sb2, String name2, LocalDate date) {
        ComparisonOutcome outcome = computeOne(gameType, sb1, name1, sb2, name2);

        boolean duo1 = isDuo(gameType, sb1);
        boolean duo2 = isDuo(gameType, sb2);
        winStreakService.applyOutcome(gameType, sb1.getUser(), duo1, sb2.getUser(), duo2, outcome, date);
        return outcome;
    }

    /** Derives a game's outcome from the two scoreboards with no persistence side effects. */
    private ComparisonOutcome computeOne(GameType gameType, Scoreboard sb1, String name1,
                                         Scoreboard sb2, String name2) {
        boolean has1 = hasResult(gameType, sb1);
        boolean has2 = hasResult(gameType, sb2);

        if (has1 && has2) {
            return switch (gameType) {
                case MINI_CROSSWORD -> miniScoreboard.determineOutcome(sb1, name1, sb2, name2);
                case MIDI_CROSSWORD -> midiScoreboard.determineOutcome(sb1, name1, sb2, name2);
                case MAIN_CROSSWORD -> mainScoreboard.determineOutcome(sb1, name1, sb2, name2);
                default -> new ComparisonOutcome.WaitingFor("");
            };
        }
        return new ComparisonOutcome.WaitingFor(has1 ? name2 : name1);
    }

    private static boolean hasResult(GameType gameType, Scoreboard sb) {
        return getter(gameType).apply(sb) != null;
    }

    private static boolean isDuo(GameType gameType, Scoreboard sb) {
        if (gameType != GameType.MAIN_CROSSWORD) return false;
        MainCrosswordResult r = sb.getMainCrosswordResult();
        return r != null && Boolean.TRUE.equals(r.getDuo());
    }

    private static Function<Scoreboard, CrosswordResult> getter(GameType gameType) {
        return switch (gameType) {
            case MINI_CROSSWORD -> Scoreboard::getMiniCrosswordResult;
            case MIDI_CROSSWORD -> Scoreboard::getMidiCrosswordResult;
            case MAIN_CROSSWORD -> Scoreboard::getMainCrosswordResult;
            default -> sb -> null;
        };
    }
}

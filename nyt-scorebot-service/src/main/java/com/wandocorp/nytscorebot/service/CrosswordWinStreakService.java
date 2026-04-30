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

    private final WinStreakService winStreakService;
    private final MiniCrosswordScoreboard miniScoreboard;
    private final MidiCrosswordScoreboard midiScoreboard;
    private final MainCrosswordScoreboard mainScoreboard;

    /**
     * Compute and apply win streak updates for all three crosswords.
     */
    public void updateAll(Scoreboard sb1, String name1, Scoreboard sb2, String name2, LocalDate date) {
        if (sb1 == null || sb2 == null) return;
        updateOne(GameType.MINI_CROSSWORD, sb1, name1, sb2, name2, date);
        updateOne(GameType.MIDI_CROSSWORD, sb1, name1, sb2, name2, date);
        updateOne(GameType.MAIN_CROSSWORD, sb1, name1, sb2, name2, date);
    }

    /**
     * Compute and apply win streak update for a single crossword game.
     */
    public void updateGame(GameType gameType, Scoreboard sb1, String name1,
                           Scoreboard sb2, String name2, LocalDate date) {
        if (sb1 == null || sb2 == null) return;
        if (!WinStreakService.isCrossword(gameType)) return;
        updateOne(gameType, sb1, name1, sb2, name2, date);
    }

    private void updateOne(GameType gameType, Scoreboard sb1, String name1,
                           Scoreboard sb2, String name2, LocalDate date) {
        boolean has1 = hasResult(gameType, sb1);
        boolean has2 = hasResult(gameType, sb2);

        ComparisonOutcome outcome;
        if (has1 && has2) {
            outcome = switch (gameType) {
                case MINI_CROSSWORD -> miniScoreboard.determineOutcome(sb1, name1, sb2, name2);
                case MIDI_CROSSWORD -> midiScoreboard.determineOutcome(sb1, name1, sb2, name2);
                case MAIN_CROSSWORD -> mainScoreboard.determineOutcome(sb1, name1, sb2, name2);
                default -> new ComparisonOutcome.WaitingFor("");
            };
        } else {
            outcome = new ComparisonOutcome.WaitingFor(has1 ? name2 : name1);
        }

        boolean duo1 = isDuo(gameType, sb1);
        boolean duo2 = isDuo(gameType, sb2);
        winStreakService.applyOutcome(gameType, sb1.getUser(), duo1, sb2.getUser(), duo2, outcome, date);
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

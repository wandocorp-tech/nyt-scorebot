package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.model.MainCrosswordResult;
import com.wandocorp.nytscorebot.service.scoreboard.ComparisonOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Detects a "triple crown" — a single player winning the Mini, Midi, and Main
 * crosswords on the same day.
 *
 * <p>The crown is derived from the {@link ComparisonOutcome} values already produced by
 * {@link CrosswordWinStreakService}, never recomputed from raw solve times, so the
 * disqualification and tie-break rules encoded in each
 * {@link com.wandocorp.nytscorebot.service.scoreboard.GameComparisonScoreboard} are honoured
 * exactly once.
 *
 * <p>Wordle, Connections, and Strands are not considered.
 */
@Slf4j
@Service
public class TripleCrownService {

    /**
     * Returns the display name of the player who swept all three crosswords, or empty
     * if no crown was earned.
     *
     * <p>Rules:
     * <ul>
     *   <li>All three crosswords must resolve to {@link ComparisonOutcome.Win} naming the
     *       same player. A {@code Tie}, {@code Nuke}, or {@code WaitingFor} on any game
     *       blocks the crown.</li>
     *   <li>A win with a null differential — the opponent failed, was disqualified, or
     *       forfeited by not submitting — counts exactly as a win with a time differential.</li>
     *   <li>A {@code duo} flag on the <em>winner's own</em> Main Crossword result blocks the
     *       crown, because the sweep was not solved alone.</li>
     * </ul>
     *
     * @param outcomes outcomes keyed by crossword game type; must contain all three
     * @param sb1      first player's scoreboard, may be null
     * @param name1    first player's display name
     * @param sb2      second player's scoreboard, may be null
     * @param name2    second player's display name
     */
    public Optional<String> detect(Map<GameType, ComparisonOutcome> outcomes,
                                   Scoreboard sb1, String name1,
                                   Scoreboard sb2, String name2) {
        if (outcomes == null || outcomes.isEmpty()) return Optional.empty();

        String winner = null;
        for (GameType gameType : CrosswordWinStreakService.CROSSWORDS) {
            String gameWinner = winnerOf(outcomes.get(gameType));
            if (gameWinner == null) return Optional.empty();
            if (winner == null) {
                winner = gameWinner;
            } else if (!winner.equals(gameWinner)) {
                return Optional.empty();
            }
        }

        Scoreboard winnerBoard = winner.equals(name1) ? sb1 : winner.equals(name2) ? sb2 : null;
        if (winnerBoard == null) {
            log.warn("Triple crown winner '{}' does not match either configured player ('{}', '{}')",
                    winner, name1, name2);
            return Optional.empty();
        }

        if (usedDuoOnMain(winnerBoard)) {
            log.debug("Triple crown withheld from {} — main crossword was flagged duo", winner);
            return Optional.empty();
        }

        return Optional.of(winner);
    }

    /**
     * Returns the winner's name for an outcome, or null when the outcome is not a win.
     * A win with a null differential label (forfeit or disqualification) counts as a win.
     */
    private static String winnerOf(ComparisonOutcome outcome) {
        return (outcome instanceof ComparisonOutcome.Win win) ? win.winnerName() : null;
    }

    /**
     * Only the winner's own duo flag is consulted. A duo flag on the losing player's result
     * does not diminish the winner's sweep.
     *
     * <p>This is deliberately narrower than {@link WinStreakService#applyOutcome}, which
     * considers both players' duo flags — do not "align" the two.
     */
    private static boolean usedDuoOnMain(Scoreboard winnerBoard) {
        MainCrosswordResult main = winnerBoard.getMainCrosswordResult();
        return main != null && Boolean.TRUE.equals(main.getDuo());
    }
}

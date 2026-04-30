package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.entity.WinStreak;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.repository.WinStreakRepository;
import com.wandocorp.nytscorebot.service.scoreboard.ComparisonOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Updates and queries head-to-head crossword win streaks.
 *
 * <p>Snapshot semantics: the first call for a (user, gameType) on a given date
 * snapshots the existing {@code currentStreak/computedDate} into
 * {@code baseStreak/baseDate}. Subsequent calls on the same date recompute
 * against the frozen base, allowing late-arriving flag changes
 * (e.g. {@code /duo}) to revise the day's streak idempotently.
 */
@RequiredArgsConstructor
@Service
public class WinStreakService {

    /** Action to apply to a (user, game) win streak based on today's outcome. */
    public enum StreakAction {
        WIN,        // increment from base if consecutive, else 1
        LOSS,       // reset to 0
        NO_CHANGE   // restore to base (Duo win, Nuke, WaitingFor before midnight)
    }

    private final WinStreakRepository repository;
    private final PuzzleCalendar puzzleCalendar;

    /**
     * Apply a head-to-head outcome for a single crossword game on the given date,
     * resolving the duo-name suffix back to the underlying user.
     *
     * @param gameType MINI/MIDI/MAIN_CROSSWORD only
     * @param userA    first player
     * @param duoA     true if userA's result has duo flag set (only meaningful for MAIN)
     * @param userB    second player
     * @param duoB     true if userB's result has duo flag set
     * @param outcome  comparison outcome from the crossword scoreboard
     * @param date     puzzle date the outcome applies to (typically today, or yesterday for the midnight job)
     */
    @Transactional
    public void applyOutcome(GameType gameType, User userA, boolean duoA,
                             User userB, boolean duoB,
                             ComparisonOutcome outcome, LocalDate date) {
        if (!isCrossword(gameType)) {
            return;
        }
        StreakAction actionA;
        StreakAction actionB;

        if (outcome instanceof ComparisonOutcome.Tie) {
            actionA = StreakAction.LOSS;
            actionB = StreakAction.LOSS;
        } else if (outcome instanceof ComparisonOutcome.Nuke
                || outcome instanceof ComparisonOutcome.WaitingFor) {
            actionA = StreakAction.NO_CHANGE;
            actionB = StreakAction.NO_CHANGE;
        } else if (outcome instanceof ComparisonOutcome.Win win) {
            // Duo win: neither streak changes
            boolean winnerIsA = resolveWinnerIsA(win, userA, duoA, userB, duoB);
            boolean winnerUsedDuo = winnerIsA ? duoA : duoB;
            if (winnerUsedDuo) {
                actionA = StreakAction.NO_CHANGE;
                actionB = StreakAction.NO_CHANGE;
            } else {
                actionA = winnerIsA ? StreakAction.WIN : StreakAction.LOSS;
                actionB = winnerIsA ? StreakAction.LOSS : StreakAction.WIN;
            }
        } else {
            return;
        }

        applyAction(userA, gameType, actionA, date);
        applyAction(userB, gameType, actionB, date);
    }

    /**
     * Apply a forfeit outcome at midnight rollover.
     * <ul>
     *   <li>Both submitted → no-op (regular outcome already ran during the day).</li>
     *   <li>One submitted → submitter is treated as a clean Win, non-submitter as a Loss.</li>
     *   <li>Neither submitted → both reset to 0.</li>
     * </ul>
     */
    @Transactional
    public void applyForfeit(GameType gameType,
                             User userA, boolean submittedA,
                             User userB, boolean submittedB,
                             LocalDate date) {
        if (!isCrossword(gameType)) {
            return;
        }
        if (submittedA && submittedB) {
            return; // already handled by the daytime computation
        }
        StreakAction actionA = submittedA ? StreakAction.WIN : StreakAction.LOSS;
        StreakAction actionB = submittedB ? StreakAction.WIN : StreakAction.LOSS;
        applyAction(userA, gameType, actionA, date);
        applyAction(userB, gameType, actionB, date);
    }

    public Map<GameType, Integer> getStreaks(User user) {
        return repository.findAllByUser(user).stream()
                .collect(Collectors.toMap(WinStreak::getGameType, WinStreak::getCurrentStreak));
    }

    public int getStreak(User user, GameType gameType) {
        return repository.findByUserAndGameType(user, gameType)
                .map(WinStreak::getCurrentStreak)
                .orElse(0);
    }

    public List<GameType> crosswordGameTypes() {
        return List.of(GameType.MINI_CROSSWORD, GameType.MIDI_CROSSWORD, GameType.MAIN_CROSSWORD);
    }

    /** Resolve which user is the winner. Falls back to display-name matching for safety. */
    private boolean resolveWinnerIsA(ComparisonOutcome.Win win,
                                     User userA, boolean duoA,
                                     User userB, boolean duoB) {
        String name = win.winnerName();
        if (name == null) return false;
        // Strip the " et al." duo suffix added by MainCrosswordScoreboard.
        String stripped = name.endsWith(" et al.") ? name.substring(0, name.length() - " et al.".length()) : name;
        if (stripped.equals(userA.getName())) return true;
        if (stripped.equals(userB.getName())) return false;
        // Defensive: if the name doesn't match either user, default to A.
        return true;
    }

    private void applyAction(User user, GameType gameType, StreakAction action, LocalDate date) {
        WinStreak streak = repository.findByUserAndGameType(user, gameType)
                .orElseGet(() -> new WinStreak(user, gameType, date));

        // Snapshot once per date: if computedDate isn't already this date,
        // freeze current value as the base before applying the new outcome.
        if (!date.equals(streak.getComputedDate())) {
            streak.setBaseStreak(streak.getCurrentStreak());
            streak.setBaseDate(streak.getComputedDate());
            streak.setComputedDate(date);
        }

        int newCurrent = switch (action) {
            case WIN -> {
                long gap = ChronoUnit.DAYS.between(streak.getBaseDate(), date);
                yield gap == 1 ? streak.getBaseStreak() + 1 : 1;
            }
            case LOSS -> 0;
            case NO_CHANGE -> streak.getBaseStreak();
        };

        streak.setCurrentStreak(newCurrent);
        streak.setLastUpdatedDate(puzzleCalendar.today());
        repository.save(streak);
    }

    static boolean isCrossword(GameType gameType) {
        return gameType == GameType.MINI_CROSSWORD
                || gameType == GameType.MIDI_CROSSWORD
                || gameType == GameType.MAIN_CROSSWORD;
    }
}

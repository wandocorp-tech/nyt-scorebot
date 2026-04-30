package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.entity.WinStreak;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.repository.WinStreakRepository;
import com.wandocorp.nytscorebot.service.scoreboard.ComparisonOutcome;
import com.wandocorp.nytscorebot.testutil.FixedPuzzleCalendar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WinStreakServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 23);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final ComparisonOutcome.Tie TIE = new ComparisonOutcome.Tie();
    private static final ComparisonOutcome.Nuke NUKE = new ComparisonOutcome.Nuke();
    private static final ComparisonOutcome.WaitingFor WAITING = new ComparisonOutcome.WaitingFor("Bob");

    private WinStreakRepository repo;
    private WinStreakService service;
    private User alice;
    private User bob;
    private final PuzzleCalendar calendar = new FixedPuzzleCalendar(TODAY);
    // Mutable storage to mimic per-user/game persistence in tests
    private Map<String, WinStreak> store;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(WinStreakRepository.class);
        store = new HashMap<>();
        when(repo.findByUserAndGameType(any(), any())).thenAnswer(inv ->
                Optional.ofNullable(store.get(key(inv.getArgument(0), inv.getArgument(1)))));
        when(repo.save(any(WinStreak.class))).thenAnswer(inv -> {
            WinStreak ws = inv.getArgument(0);
            store.put(key(ws.getUser(), ws.getGameType()), ws);
            return ws;
        });
        when(repo.findAllByUser(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return store.values().stream().filter(w -> w.getUser() == u).toList();
        });

        service = new WinStreakService(repo, calendar);
        alice = new User("c1", "Alice", "u1");
        bob = new User("c2", "Bob", "u2");
    }

    private static String key(User u, GameType g) {
        return u.getDiscordUserId() + "|" + g.name();
    }

    private ComparisonOutcome.Win winFor(String name) {
        return new ComparisonOutcome.Win(name, "x");
    }

    // ── Outcome → action mapping ─────────────────────────────────────────────

    @Test
    void winIncrementsWinnerAndResetsLoser() {
        service.applyOutcome(GameType.MINI_CROSSWORD, alice, false, bob, false, winFor("Alice"), TODAY);
        assertThat(service.getStreak(alice, GameType.MINI_CROSSWORD)).isEqualTo(1);
        assertThat(service.getStreak(bob, GameType.MINI_CROSSWORD)).isZero();
    }

    @Test
    void tieResetsBothToZero() {
        // Seed prior streaks
        service.applyOutcome(GameType.MIDI_CROSSWORD, alice, false, bob, false, winFor("Alice"), YESTERDAY);
        service.applyOutcome(GameType.MIDI_CROSSWORD, alice, false, bob, false, TIE, TODAY);
        assertThat(service.getStreak(alice, GameType.MIDI_CROSSWORD)).isZero();
        assertThat(service.getStreak(bob, GameType.MIDI_CROSSWORD)).isZero();
    }

    @Test
    void nukeIsNoChange() {
        service.applyOutcome(GameType.MAIN_CROSSWORD, alice, false, bob, false, winFor("Alice"), YESTERDAY);
        service.applyOutcome(GameType.MAIN_CROSSWORD, alice, false, bob, false, NUKE, TODAY);
        assertThat(service.getStreak(alice, GameType.MAIN_CROSSWORD)).isEqualTo(1);
        assertThat(service.getStreak(bob, GameType.MAIN_CROSSWORD)).isZero();
    }

    @Test
    void waitingForIsNoChange() {
        service.applyOutcome(GameType.MINI_CROSSWORD, alice, false, bob, false, winFor("Alice"), YESTERDAY);
        service.applyOutcome(GameType.MINI_CROSSWORD, alice, false, bob, false, WAITING, TODAY);
        assertThat(service.getStreak(alice, GameType.MINI_CROSSWORD)).isEqualTo(1);
    }

    @Test
    void duoWinDoesNotChangeEitherStreak() {
        service.applyOutcome(GameType.MAIN_CROSSWORD, alice, false, bob, false, winFor("Alice"), YESTERDAY);
        service.applyOutcome(GameType.MAIN_CROSSWORD, alice, true, bob, false, winFor("Alice et al."), TODAY);
        assertThat(service.getStreak(alice, GameType.MAIN_CROSSWORD)).isEqualTo(1);
        assertThat(service.getStreak(bob, GameType.MAIN_CROSSWORD)).isZero();
    }

    @Test
    void nonCrosswordGameIsIgnored() {
        service.applyOutcome(GameType.WORDLE, alice, false, bob, false, winFor("Alice"), TODAY);
        verify(repo, never()).save(any());
    }

    // ── Snapshot / idempotent same-day updates ───────────────────────────────

    @Test
    void sameDayRevisionRecomputesAgainstFrozenBase() {
        // Day 1: Alice wins → 1
        service.applyOutcome(GameType.MAIN_CROSSWORD, alice, false, bob, false, winFor("Alice"), YESTERDAY);
        // Day 2: Alice wins again → 2
        service.applyOutcome(GameType.MAIN_CROSSWORD, alice, false, bob, false, winFor("Alice"), TODAY);
        assertThat(service.getStreak(alice, GameType.MAIN_CROSSWORD)).isEqualTo(2);

        // Same day: revise to a Tie → reset to 0 (base for today is 1, but TIE resets)
        service.applyOutcome(GameType.MAIN_CROSSWORD, alice, false, bob, false, TIE, TODAY);
        assertThat(service.getStreak(alice, GameType.MAIN_CROSSWORD)).isZero();

        // Same day again: revise back to a Win → should be base+1=2, not 1
        service.applyOutcome(GameType.MAIN_CROSSWORD, alice, false, bob, false, winFor("Alice"), TODAY);
        assertThat(service.getStreak(alice, GameType.MAIN_CROSSWORD)).isEqualTo(2);
    }

    // ── Gap detection ────────────────────────────────────────────────────────

    @Test
    void gapGreaterThanOneRestartsStreakAtOne() {
        service.applyOutcome(GameType.MIDI_CROSSWORD, alice, false, bob, false, winFor("Alice"), TODAY.minusDays(5));
        // No updates between → next win 5 days later starts from 1
        service.applyOutcome(GameType.MIDI_CROSSWORD, alice, false, bob, false, winFor("Alice"), TODAY);
        assertThat(service.getStreak(alice, GameType.MIDI_CROSSWORD)).isEqualTo(1);
    }

    // ── Forfeit (midnight rollover) ──────────────────────────────────────────

    @Test
    void forfeitOnlyAliceSubmittedTreatsAsWin() {
        service.applyForfeit(GameType.MAIN_CROSSWORD, alice, true, bob, false, TODAY);
        assertThat(service.getStreak(alice, GameType.MAIN_CROSSWORD)).isEqualTo(1);
        assertThat(service.getStreak(bob, GameType.MAIN_CROSSWORD)).isZero();
    }

    @Test
    void forfeitNeitherSubmittedResetsBoth() {
        service.applyOutcome(GameType.MAIN_CROSSWORD, alice, false, bob, false, winFor("Alice"), YESTERDAY);
        service.applyForfeit(GameType.MAIN_CROSSWORD, alice, false, bob, false, TODAY);
        assertThat(service.getStreak(alice, GameType.MAIN_CROSSWORD)).isZero();
        assertThat(service.getStreak(bob, GameType.MAIN_CROSSWORD)).isZero();
    }

    @Test
    void forfeitBothSubmittedIsNoOp() {
        service.applyOutcome(GameType.MAIN_CROSSWORD, alice, false, bob, false, winFor("Alice"), TODAY);
        int before = service.getStreak(alice, GameType.MAIN_CROSSWORD);
        service.applyForfeit(GameType.MAIN_CROSSWORD, alice, true, bob, true, TODAY);
        assertThat(service.getStreak(alice, GameType.MAIN_CROSSWORD)).isEqualTo(before);
    }

    @Test
    void forfeitNonCrosswordIsIgnored() {
        service.applyForfeit(GameType.WORDLE, alice, true, bob, false, TODAY);
        verify(repo, never()).save(any());
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    @Test
    void getStreaksAggregatesAllGameTypes() {
        service.applyOutcome(GameType.MINI_CROSSWORD, alice, false, bob, false, winFor("Alice"), TODAY);
        service.applyOutcome(GameType.MIDI_CROSSWORD, alice, false, bob, false, winFor("Alice"), TODAY);
        Map<GameType, Integer> map = service.getStreaks(alice);
        assertThat(map).containsEntry(GameType.MINI_CROSSWORD, 1).containsEntry(GameType.MIDI_CROSSWORD, 1);
    }

    @Test
    void getStreakReturnsZeroWhenAbsent() {
        assertThat(service.getStreak(alice, GameType.MAIN_CROSSWORD)).isZero();
    }

    @Test
    void crosswordGameTypesReturnsAllThree() {
        assertThat(service.crosswordGameTypes())
                .containsExactly(GameType.MINI_CROSSWORD, GameType.MIDI_CROSSWORD, GameType.MAIN_CROSSWORD);
    }

    // ── Winner-name resolution defenses ──────────────────────────────────────

    @Test
    void unknownWinnerNameDefaultsToUserA() {
        service.applyOutcome(GameType.MAIN_CROSSWORD, alice, false, bob, false, winFor("Stranger"), TODAY);
        assertThat(service.getStreak(alice, GameType.MAIN_CROSSWORD)).isEqualTo(1);
        assertThat(service.getStreak(bob, GameType.MAIN_CROSSWORD)).isZero();
    }

    @Test
    void nullWinnerNameDoesNotCrash() {
        service.applyOutcome(GameType.MAIN_CROSSWORD, alice, false, bob, false,
                new ComparisonOutcome.Win(null, "x"), TODAY);
        // null winnerName resolves to !winnerIsA → bob gets WIN
        assertThat(service.getStreak(bob, GameType.MAIN_CROSSWORD)).isEqualTo(1);
    }

    @Test
    void duoSuffixIsStrippedBeforeMatching() {
        service.applyOutcome(GameType.MAIN_CROSSWORD, alice, false, bob, true,
                winFor("Bob et al."), TODAY);
        // Bob wins via duo → no change
        assertThat(service.getStreak(bob, GameType.MAIN_CROSSWORD)).isZero();
        assertThat(service.getStreak(alice, GameType.MAIN_CROSSWORD)).isZero();
    }

    @Test
    void allCrosswordGameTypesAreTreatedAsCrossword() {
        for (GameType gt : List.of(GameType.MINI_CROSSWORD, GameType.MIDI_CROSSWORD, GameType.MAIN_CROSSWORD)) {
            assertThat(WinStreakService.isCrossword(gt)).isTrue();
        }
        assertThat(WinStreakService.isCrossword(GameType.WORDLE)).isFalse();
    }
}

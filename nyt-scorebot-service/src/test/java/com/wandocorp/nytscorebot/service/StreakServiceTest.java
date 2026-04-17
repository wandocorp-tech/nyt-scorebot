package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.entity.Streak;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.ConnectionsResult;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.model.MiniCrosswordResult;
import com.wandocorp.nytscorebot.model.StrandsResult;
import com.wandocorp.nytscorebot.model.WordleResult;
import com.wandocorp.nytscorebot.repository.StreakRepository;
import com.wandocorp.nytscorebot.testutil.FixedPuzzleCalendar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StreakServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 23);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    private StreakRepository streakRepo;
    private StreakService service;
    private User user;

    private final PuzzleCalendar calendar = new FixedPuzzleCalendar(TODAY);

    @BeforeEach
    void setUp() {
        streakRepo = Mockito.mock(StreakRepository.class);
        when(streakRepo.save(any(Streak.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new StreakService(streakRepo, calendar);
        user = new User("ch1", "TestUser", "u1");
    }

    // ── Consecutive-day increment ────────────────────────────────────────────

    @Test
    void consecutiveDayWordleSuccessIncrementsStreak() {
        Streak existing = new Streak(user, GameType.WORDLE, 3, YESTERDAY);
        when(streakRepo.findByUserAndGameType(user, GameType.WORDLE))
                .thenReturn(Optional.of(existing));

        WordleResult result = new WordleResult("raw", "author", null, 100, 4, true, false);
        service.updateStreak(user, result);

        assertThat(existing.getCurrentStreak()).isEqualTo(4);
        assertThat(existing.getLastUpdatedDate()).isEqualTo(TODAY);
    }

    @Test
    void consecutiveDayConnectionsSuccessIncrementsStreak() {
        Streak existing = new Streak(user, GameType.CONNECTIONS, 7, YESTERDAY);
        when(streakRepo.findByUserAndGameType(user, GameType.CONNECTIONS))
                .thenReturn(Optional.of(existing));

        ConnectionsResult result = new ConnectionsResult("raw", "author", null, 100, 0, true, List.of());
        service.updateStreak(user, result);

        assertThat(existing.getCurrentStreak()).isEqualTo(8);
    }

    // ── Gap reset then success (streak = 1) ──────────────────────────────────

    @Test
    void gapResetThenSuccessSetsStreakToOne() {
        Streak existing = new Streak(user, GameType.WORDLE, 5, TODAY.minusDays(3));
        when(streakRepo.findByUserAndGameType(user, GameType.WORDLE))
                .thenReturn(Optional.of(existing));

        WordleResult result = new WordleResult("raw", "author", null, 100, 3, true, false);
        service.updateStreak(user, result);

        assertThat(existing.getCurrentStreak()).isEqualTo(1);
        assertThat(existing.getLastUpdatedDate()).isEqualTo(TODAY);
    }

    // ── Gap reset then failure (streak = 0) ──────────────────────────────────

    @Test
    void gapResetThenFailureSetsStreakToZero() {
        Streak existing = new Streak(user, GameType.WORDLE, 5, TODAY.minusDays(2));
        when(streakRepo.findByUserAndGameType(user, GameType.WORDLE))
                .thenReturn(Optional.of(existing));

        WordleResult result = new WordleResult("raw", "author", null, 100, 6, false, false);
        service.updateStreak(user, result);

        assertThat(existing.getCurrentStreak()).isEqualTo(0);
        assertThat(existing.getLastUpdatedDate()).isEqualTo(TODAY);
    }

    // ── First-ever submission ────────────────────────────────────────────────

    @Test
    void firstEverSubmissionCreatesStreakOfOne() {
        when(streakRepo.findByUserAndGameType(user, GameType.WORDLE))
                .thenReturn(Optional.empty());
        when(streakRepo.save(any(Streak.class))).thenAnswer(inv -> inv.getArgument(0));

        WordleResult result = new WordleResult("raw", "author", null, 100, 3, true, false);
        service.updateStreak(user, result);

        // save called once with streak=1 and lastUpdatedDate=today
        verify(streakRepo, times(1)).save(argThat(s ->
                s.getCurrentStreak() == 1 && s.getLastUpdatedDate().equals(TODAY)));
    }

    @Test
    void firstEverFailedSubmissionCreatesStreakOfZero() {
        when(streakRepo.findByUserAndGameType(user, GameType.WORDLE))
                .thenReturn(Optional.empty());
        when(streakRepo.save(any(Streak.class))).thenAnswer(inv -> inv.getArgument(0));

        WordleResult result = new WordleResult("raw", "author", null, 100, 6, false, false);
        service.updateStreak(user, result);

        // save called once with streak=0 and lastUpdatedDate=today
        verify(streakRepo, times(1)).save(argThat(s ->
                s.getCurrentStreak() == 0 && s.getLastUpdatedDate().equals(TODAY)));
    }

    // ── Strands always succeeds ──────────────────────────────────────────────

    @Test
    void strandsAlwaysSucceeds() {
        Streak existing = new Streak(user, GameType.STRANDS, 10, YESTERDAY);
        when(streakRepo.findByUserAndGameType(user, GameType.STRANDS))
                .thenReturn(Optional.of(existing));

        StrandsResult result = new StrandsResult("raw", "author", null, 100, 3);
        service.updateStreak(user, result);

        assertThat(existing.getCurrentStreak()).isEqualTo(11);
    }

    // ── Wordle failure resets ────────────────────────────────────────────────

    @Test
    void wordleFailureResetsStreakToZero() {
        Streak existing = new Streak(user, GameType.WORDLE, 8, YESTERDAY);
        when(streakRepo.findByUserAndGameType(user, GameType.WORDLE))
                .thenReturn(Optional.of(existing));

        WordleResult result = new WordleResult("raw", "author", null, 100, 6, false, false);
        service.updateStreak(user, result);

        assertThat(existing.getCurrentStreak()).isEqualTo(0);
        assertThat(existing.getLastUpdatedDate()).isEqualTo(TODAY);
    }

    // ── Connections failure resets ────────────────────────────────────────────

    @Test
    void connectionsFailureResetsStreakToZero() {
        Streak existing = new Streak(user, GameType.CONNECTIONS, 4, YESTERDAY);
        when(streakRepo.findByUserAndGameType(user, GameType.CONNECTIONS))
                .thenReturn(Optional.of(existing));

        ConnectionsResult result = new ConnectionsResult("raw", "author", null, 100, 4, false, List.of());
        service.updateStreak(user, result);

        assertThat(existing.getCurrentStreak()).isEqualTo(0);
    }

    // ── Crossword results are ignored ────────────────────────────────────────

    @Test
    void crosswordResultDoesNotCreateStreak() {
        MiniCrosswordResult result = new MiniCrosswordResult("raw", "author", null,
                "0:30", 30, TODAY);
        service.updateStreak(user, result);

        verify(streakRepo, never()).findByUserAndGameType(any(), any());
        verify(streakRepo, never()).save(any());
    }

    // ── Same-day no-op ───────────────────────────────────────────────────────

    @Test
    void sameDayUpdateIsNoOp() {
        Streak existing = new Streak(user, GameType.WORDLE, 5, TODAY);
        when(streakRepo.findByUserAndGameType(user, GameType.WORDLE))
                .thenReturn(Optional.of(existing));

        WordleResult result = new WordleResult("raw", "author", null, 100, 3, true, false);
        service.updateStreak(user, result);

        // Streak should not change — only the initial find happens, no save for update
        assertThat(existing.getCurrentStreak()).isEqualTo(5);
        verify(streakRepo, never()).save(any());
    }

    // ── setStreak ────────────────────────────────────────────────────────────

    @Test
    void setStreakUpdatesExistingRecord() {
        Streak existing = new Streak(user, GameType.WORDLE, 3, YESTERDAY);
        when(streakRepo.findByUserAndGameType(user, GameType.WORDLE))
                .thenReturn(Optional.of(existing));

        service.setStreak(user, GameType.WORDLE, 10);

        assertThat(existing.getCurrentStreak()).isEqualTo(10);
        assertThat(existing.getLastUpdatedDate()).isEqualTo(TODAY);
        verify(streakRepo).save(existing);
    }

    @Test
    void setStreakCreatesNewRecordWhenNoneExists() {
        when(streakRepo.findByUserAndGameType(user, GameType.WORDLE))
                .thenReturn(Optional.empty());

        service.setStreak(user, GameType.WORDLE, 5);

        verify(streakRepo).save(any(Streak.class));
    }

    // ── getStreaks / getStreak ────────────────────────────────────────────────

    @Test
    void getStreaksReturnsMapOfGameTypeToStreak() {
        Streak wordleStreak = new Streak(user, GameType.WORDLE, 5, TODAY);
        Streak strandsStreak = new Streak(user, GameType.STRANDS, 3, TODAY);
        when(streakRepo.findAllByUser(user)).thenReturn(List.of(wordleStreak, strandsStreak));

        var streaks = service.getStreaks(user);

        assertThat(streaks).containsEntry(GameType.WORDLE, 5);
        assertThat(streaks).containsEntry(GameType.STRANDS, 3);
        assertThat(streaks).hasSize(2);
    }

    @Test
    void getStreakReturnsZeroWhenNoRecord() {
        when(streakRepo.findByUserAndGameType(user, GameType.WORDLE))
                .thenReturn(Optional.empty());

        assertThat(service.getStreak(user, GameType.WORDLE)).isEqualTo(0);
    }

    @Test
    void getStreakReturnsCurrentValue() {
        Streak existing = new Streak(user, GameType.WORDLE, 7, TODAY);
        when(streakRepo.findByUserAndGameType(user, GameType.WORDLE))
                .thenReturn(Optional.of(existing));

        assertThat(service.getStreak(user, GameType.WORDLE)).isEqualTo(7);
    }

    // ── resolveGameType / isSuccess ──────────────────────────────────────────

    @Test
    void resolveGameTypeReturnsNullForCrossword() {
        assertThat(StreakService.resolveGameType(
                new MiniCrosswordResult("raw", "a", null, "0:30", 30, TODAY)))
                .isNull();
    }

    @Test
    void isSuccessReturnsTrueForCompletedWordle() {
        assertThat(StreakService.isSuccess(
                new WordleResult("raw", "a", null, 1, 3, true, false))).isTrue();
    }

    @Test
    void isSuccessReturnsFalseForFailedWordle() {
        assertThat(StreakService.isSuccess(
                new WordleResult("raw", "a", null, 1, 6, false, false))).isFalse();
    }

    @Test
    void isSuccessAlwaysTrueForStrands() {
        assertThat(StreakService.isSuccess(
                new StrandsResult("raw", "a", null, 1, 5))).isTrue();
    }
}

package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.*;
import com.wandocorp.nytscorebot.repository.ScoreboardRepository;
import com.wandocorp.nytscorebot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ScoreboardService} validation and deduplication logic.
 * Uses Mockito for repositories and a concrete stub for PuzzleCalendar.
 */
class ScoreboardServiceTest {

    private static final String CHANNEL = "123456";
    private static final String PERSON = "TestUser";
    private static final String USER_ID = "789";
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 23);

    private UserRepository userRepo;
    private ScoreboardRepository scoreboardRepo;
    private ScoreboardService service;

    private User user;
    private Scoreboard scoreboard;

    /** Stub that pins "today" to a fixed date so tests are deterministic. */
    private static class FixedPuzzleCalendar extends PuzzleCalendar {
        @Override
        public LocalDate today() {
            return TODAY;
        }
        @Override
        public int expectedWordle() {
            return expectedWordle(TODAY);
        }
        @Override
        public int expectedConnections() {
            return expectedConnections(TODAY);
        }
        @Override
        public int expectedStrands() {
            return expectedStrands(TODAY);
        }
    }

    private final PuzzleCalendar calendar = new FixedPuzzleCalendar();

    @BeforeEach
    void setUp() {
        userRepo = Mockito.mock(UserRepository.class);
        scoreboardRepo = Mockito.mock(ScoreboardRepository.class);

        user = new User(CHANNEL, PERSON, USER_ID);
        scoreboard = new Scoreboard(user, TODAY);

        when(userRepo.findByChannelId(CHANNEL)).thenReturn(Optional.of(user));
        when(userRepo.findByUserId(USER_ID)).thenReturn(Optional.of(user));
        when(scoreboardRepo.findByUserAndDate(user, TODAY)).thenReturn(Optional.of(scoreboard));
        when(scoreboardRepo.save(any(Scoreboard.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new ScoreboardService(userRepo, scoreboardRepo, calendar);
    }

    // ── Puzzle number validation ─────────────────────────────────────────────

    @Test
    void wordleWithCorrectPuzzleNumberIsSaved() {
        int expected = calendar.expectedWordle();
        WordleResult result = new WordleResult("raw", PERSON, null, expected, 3, true, false);

        assertThat(service.saveResult(CHANNEL, PERSON, USER_ID, result)).isEqualTo(SaveOutcome.SAVED);
        verify(scoreboardRepo).save(any(Scoreboard.class));
    }

    @Test
    void wordleWithWrongPuzzleNumberIsRejected() {
        WordleResult result = new WordleResult("raw", PERSON, null, 1, 3, true, false);

        assertThat(service.saveResult(CHANNEL, PERSON, USER_ID, result)).isEqualTo(SaveOutcome.WRONG_PUZZLE_NUMBER);
        verify(scoreboardRepo, never()).save(any(Scoreboard.class));
    }

    @Test
    void connectionsWithWrongPuzzleNumberIsRejected() {
        ConnectionsResult result = new ConnectionsResult("raw", PERSON, null, 1, 0, true, List.of());

        assertThat(service.saveResult(CHANNEL, PERSON, USER_ID, result)).isEqualTo(SaveOutcome.WRONG_PUZZLE_NUMBER);
        verify(scoreboardRepo, never()).save(any(Scoreboard.class));
    }

    @Test
    void strandsWithWrongPuzzleNumberIsRejected() {
        StrandsResult result = new StrandsResult("raw", PERSON, null, 1, 0);

        assertThat(service.saveResult(CHANNEL, PERSON, USER_ID, result)).isEqualTo(SaveOutcome.WRONG_PUZZLE_NUMBER);
        verify(scoreboardRepo, never()).save(any(Scoreboard.class));
    }

    // ── Crossword date validation ────────────────────────────────────────────

    @Test
    void crosswordWithTodaysDateIsSaved() {
        CrosswordResult result = new CrosswordResult("raw", PERSON, null,
                CrosswordType.MAIN, "5:00", 300, TODAY);

        assertThat(service.saveResult(CHANNEL, PERSON, USER_ID, result)).isEqualTo(SaveOutcome.SAVED);
    }

    @Test
    void crosswordWithPreviousDateIsAccepted() {
        LocalDate yesterday = TODAY.minusDays(1);
        Scoreboard yesterdayBoard = new Scoreboard(user, yesterday);
        when(scoreboardRepo.findByUserAndDate(user, yesterday)).thenReturn(Optional.of(yesterdayBoard));

        CrosswordResult result = new CrosswordResult("raw", PERSON, null,
                CrosswordType.MAIN, "5:00", 300, yesterday);

        assertThat(service.saveResult(CHANNEL, PERSON, USER_ID, result)).isEqualTo(SaveOutcome.SAVED);
        verify(scoreboardRepo).save(any(Scoreboard.class));
    }

    // ── Deduplication ────────────────────────────────────────────────────────

    @Test
    void duplicateWordleIsRejected() {
        int expected = calendar.expectedWordle();
        WordleResult first = new WordleResult("raw", PERSON, null, expected, 3, true, false);
        scoreboard.setWordleResult(first);

        WordleResult second = new WordleResult("raw2", PERSON, null, expected, 4, true, false);
        assertThat(service.saveResult(CHANNEL, PERSON, USER_ID, second)).isEqualTo(SaveOutcome.ALREADY_SUBMITTED);
    }

    @Test
    void duplicateMiniCrosswordIsRejected() {
        CrosswordResult first = new CrosswordResult("raw", PERSON, null,
                CrosswordType.MINI, "0:30", 30, TODAY);
        scoreboard.setMiniCrosswordResult(first);

        CrosswordResult second = new CrosswordResult("raw2", PERSON, null,
                CrosswordType.MINI, "0:25", 25, TODAY);
        assertThat(service.saveResult(CHANNEL, PERSON, USER_ID, second)).isEqualTo(SaveOutcome.ALREADY_SUBMITTED);
    }

    @Test
    void differentGameTypesCanBothBeSaved() {
        int expectedWordle = calendar.expectedWordle();
        WordleResult wordle = new WordleResult("raw", PERSON, null, expectedWordle, 3, true, false);
        assertThat(service.saveResult(CHANNEL, PERSON, USER_ID, wordle)).isEqualTo(SaveOutcome.SAVED);

        int expectedConnections = calendar.expectedConnections();
        ConnectionsResult connections = new ConnectionsResult("raw", PERSON, null,
                expectedConnections, 0, true, List.of("🟩", "🟨", "🟦", "🟪"));
        assertThat(service.saveResult(CHANNEL, PERSON, USER_ID, connections)).isEqualTo(SaveOutcome.SAVED);
    }

    // ── applyResult branches ─────────────────────────────────────────────────

    @Test
    void correctStrandsResultIsSaved() {
        int expected = calendar.expectedStrands();
        StrandsResult result = new StrandsResult("raw", PERSON, null, expected, 0);

        assertThat(service.saveResult(CHANNEL, PERSON, USER_ID, result)).isEqualTo(SaveOutcome.SAVED);
        verify(scoreboardRepo).save(any(Scoreboard.class));
    }

    @Test
    void midiCrosswordWithTodaysDateIsSaved() {
        CrosswordResult result = new CrosswordResult("raw", PERSON, null,
                CrosswordType.MIDI, "3:00", 180, TODAY);

        assertThat(service.saveResult(CHANNEL, PERSON, USER_ID, result)).isEqualTo(SaveOutcome.SAVED);
    }

    @Test
    void miniCrosswordWithTodaysDateIsSaved() {
        CrosswordResult result = new CrosswordResult("raw", PERSON, null,
                CrosswordType.MINI, "0:30", 30, TODAY);

        assertThat(service.saveResult(CHANNEL, PERSON, USER_ID, result)).isEqualTo(SaveOutcome.SAVED);
    }

    @Test
    void crosswordWithNullDateIsAlwaysSaved() {
        // A crossword result with no extracted date (null) skips date validation
        CrosswordResult result = new CrosswordResult("raw", PERSON, null,
                CrosswordType.MINI, "0:30", 30, null);

        assertThat(service.saveResult(CHANNEL, PERSON, USER_ID, result)).isEqualTo(SaveOutcome.SAVED);
    }

    // ── Entity creation paths ────────────────────────────────────────────────

    @Test
    void newUserIsCreatedWhenNoneExists() {
        when(userRepo.findByChannelId(CHANNEL)).thenReturn(Optional.empty());
        when(userRepo.save(any(User.class))).thenReturn(user);
        when(scoreboardRepo.findByUserAndDate(user, TODAY)).thenReturn(Optional.of(scoreboard));

        int expected = calendar.expectedWordle();
        WordleResult result = new WordleResult("raw", PERSON, null, expected, 3, true, false);

        assertThat(service.saveResult(CHANNEL, PERSON, USER_ID, result)).isEqualTo(SaveOutcome.SAVED);
        verify(userRepo).save(any(User.class));
    }

    @Test
    void newScoreboardIsCreatedWhenNoneExists() {
        when(scoreboardRepo.findByUserAndDate(user, TODAY)).thenReturn(Optional.empty());
        when(scoreboardRepo.save(any(Scoreboard.class))).thenReturn(scoreboard);

        int expected = calendar.expectedWordle();
        WordleResult result = new WordleResult("raw", PERSON, null, expected, 3, true, false);

        // First save creates the scoreboard, second save persists the result
        assertThat(service.saveResult(CHANNEL, PERSON, USER_ID, result)).isEqualTo(SaveOutcome.SAVED);
        verify(scoreboardRepo, times(2)).save(any(Scoreboard.class));
    }

    // ── markFinished ─────────────────────────────────────────────────────────

    @Test
    void markFinishedReturnsMarkedFinishedWhenScoreboardExists() {
        assertThat(service.markFinished(USER_ID, TODAY)).isEqualTo(MarkFinishedOutcome.MARKED_FINISHED);
        assertThat(scoreboard.isFinished()).isTrue();
        verify(scoreboardRepo).save(scoreboard);
    }

    @Test
    void markFinishedReturnsAlreadyFinishedWhenAlreadyDone() {
        scoreboard.setFinished(true);
        assertThat(service.markFinished(USER_ID, TODAY)).isEqualTo(MarkFinishedOutcome.ALREADY_FINISHED);
        verify(scoreboardRepo, never()).save(any(Scoreboard.class));
    }

    @Test
    void markFinishedReturnsNoScoreboardWhenNoneForDate() {
        LocalDate tomorrow = TODAY.plusDays(1);
        when(scoreboardRepo.findByUserAndDate(user, tomorrow)).thenReturn(Optional.empty());
        assertThat(service.markFinished(USER_ID, tomorrow)).isEqualTo(MarkFinishedOutcome.NO_SCOREBOARD_FOR_DATE);
    }

    @Test
    void markFinishedReturnsUserNotFoundForUnknownDiscordId() {
        when(userRepo.findByUserId("unknown-id")).thenReturn(Optional.empty());
        assertThat(service.markFinished("unknown-id", TODAY)).isEqualTo(MarkFinishedOutcome.USER_NOT_FOUND);
    }
}

package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.*;
import com.wandocorp.nytscorebot.repository.ScoreboardRepository;
import com.wandocorp.nytscorebot.repository.UserRepository;
import com.wandocorp.nytscorebot.testutil.FixedPuzzleCalendar;
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
    private StreakService streakService;
    private ScoreboardService service;

    private User user;
    private Scoreboard scoreboard;

    private final PuzzleCalendar calendar = new FixedPuzzleCalendar(TODAY);

    @BeforeEach
    void setUp() {
        userRepo = Mockito.mock(UserRepository.class);
        scoreboardRepo = Mockito.mock(ScoreboardRepository.class);
        streakService = Mockito.mock(StreakService.class);

        user = new User(CHANNEL, PERSON, USER_ID);
        scoreboard = new Scoreboard(user, TODAY);

        when(userRepo.findByChannelId(CHANNEL)).thenReturn(Optional.of(user));
        when(userRepo.findByDiscordUserId(USER_ID)).thenReturn(Optional.of(user));
        when(scoreboardRepo.findByUserAndDate(user, TODAY)).thenReturn(Optional.of(scoreboard));
        when(scoreboardRepo.save(any(Scoreboard.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new ScoreboardService(userRepo, scoreboardRepo, calendar, streakService);
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
        MainCrosswordResult result = new MainCrosswordResult("raw", PERSON, null,
                "5:00", 300, TODAY);

        assertThat(service.saveResult(CHANNEL, PERSON, USER_ID, result)).isEqualTo(SaveOutcome.SAVED);
    }

    @Test
    void crosswordWithPreviousDateIsAccepted() {
        LocalDate yesterday = TODAY.minusDays(1);
        Scoreboard yesterdayBoard = new Scoreboard(user, yesterday);
        when(scoreboardRepo.findByUserAndDate(user, yesterday)).thenReturn(Optional.of(yesterdayBoard));

        MainCrosswordResult result = new MainCrosswordResult("raw", PERSON, null,
                "5:00", 300, yesterday);

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
        when(userRepo.findByDiscordUserId("unknown-id")).thenReturn(Optional.empty());
        assertThat(service.markFinished("unknown-id", TODAY)).isEqualTo(MarkFinishedOutcome.USER_NOT_FOUND);
    }

    @Test
    void allSixGamesAutoSetsFinished() {
        // Save one of each game type; the last save should trigger auto-finish
        int expectedWordle = calendar.expectedWordle();
        int expectedConnections = calendar.expectedConnections();
        int expectedStrands = calendar.expectedStrands();

        WordleResult wordle = new WordleResult("raw", PERSON, null, expectedWordle, 3, true, false);
        ConnectionsResult connections = new ConnectionsResult("raw", PERSON, null, expectedConnections, 0, true, List.of());
        StrandsResult strands = new StrandsResult("raw", PERSON, null, expectedStrands, 0);
        CrosswordResult mini = new CrosswordResult("raw", PERSON, null, CrosswordType.MINI, "1:23", 83, TODAY);
        CrosswordResult midi = new CrosswordResult("raw", PERSON, null, CrosswordType.MIDI, "3:45", 225, TODAY);
        MainCrosswordResult main = new MainCrosswordResult("raw", PERSON, null, "15:00", 900, TODAY);

        Scoreboard scoreboard = new Scoreboard(user, TODAY);
        when(scoreboardRepo.findByUserAndDate(user, TODAY)).thenReturn(Optional.of(scoreboard));

        // Save first 5 games — finished should remain false
        service.saveResult(CHANNEL, PERSON, USER_ID, wordle);
        assertThat(scoreboard.isFinished()).isFalse();

        service.saveResult(CHANNEL, PERSON, USER_ID, connections);
        assertThat(scoreboard.isFinished()).isFalse();

        service.saveResult(CHANNEL, PERSON, USER_ID, strands);
        assertThat(scoreboard.isFinished()).isFalse();

        service.saveResult(CHANNEL, PERSON, USER_ID, mini);
        assertThat(scoreboard.isFinished()).isFalse();

        service.saveResult(CHANNEL, PERSON, USER_ID, midi);
        assertThat(scoreboard.isFinished()).isFalse();

        // Save 6th game — should auto-set finished
        service.saveResult(CHANNEL, PERSON, USER_ID, main);
        assertThat(scoreboard.isFinished()).isTrue();
    }

    // ── saveResult stores MAIN crossword as MainCrosswordResult ─────────────

    @Test
    void mainCrosswordIsStoredAsMainCrosswordResult() {
        MainCrosswordResult result = new MainCrosswordResult("raw", PERSON, null,
                "15:00", 900, TODAY);
        assertThat(service.saveResult(CHANNEL, PERSON, USER_ID, result)).isEqualTo(SaveOutcome.SAVED);
        assertThat(scoreboard.getMainCrosswordResult()).isInstanceOf(MainCrosswordResult.class);
        assertThat(scoreboard.getMainCrosswordResult().getDuo()).isNull();
    }

    // ── toggleDuo ────────────────────────────────────────────────────────────

    @Test
    void toggleDuoSetsWhenNull() {
        setUpMainCrossword();
        assertThat(service.toggleDuo(USER_ID, TODAY)).isEqualTo(SetFlagOutcome.FLAG_SET);
        assertThat(scoreboard.getMainCrosswordResult().getDuo()).isTrue();
    }

    @Test
    void toggleDuoClearsWhenTrue() {
        setUpMainCrossword();
        scoreboard.getMainCrosswordResult().setDuo(true);
        assertThat(service.toggleDuo(USER_ID, TODAY)).isEqualTo(SetFlagOutcome.FLAG_CLEARED);
        assertThat(scoreboard.getMainCrosswordResult().getDuo()).isFalse();
    }

    @Test
    void toggleDuoReturnsNoMainCrosswordWhenAbsent() {
        assertThat(service.toggleDuo(USER_ID, TODAY)).isEqualTo(SetFlagOutcome.NO_MAIN_CROSSWORD);
    }

    @Test
    void toggleDuoReturnsNoScoreboardWhenNone() {
        LocalDate tomorrow = TODAY.plusDays(1);
        when(scoreboardRepo.findByUserAndDate(user, tomorrow)).thenReturn(Optional.empty());
        assertThat(service.toggleDuo(USER_ID, tomorrow)).isEqualTo(SetFlagOutcome.NO_SCOREBOARD_FOR_DATE);
    }

    @Test
    void toggleDuoReturnsUserNotFoundForUnknownUser() {
        when(userRepo.findByDiscordUserId("unknown")).thenReturn(Optional.empty());
        assertThat(service.toggleDuo("unknown", TODAY)).isEqualTo(SetFlagOutcome.USER_NOT_FOUND);
    }

    // ── setLookups ───────────────────────────────────────────────────────────

    @Test
    void setLookupsSetsPositiveValue() {
        setUpMainCrossword();
        assertThat(service.setLookups(USER_ID, TODAY, 3)).isEqualTo(SetFlagOutcome.FLAG_SET);
        assertThat(scoreboard.getMainCrosswordResult().getLookups()).isEqualTo(3);
    }

    @Test
    void setLookupsZeroClearsValue() {
        setUpMainCrossword();
        scoreboard.getMainCrosswordResult().setLookups(5);
        assertThat(service.setLookups(USER_ID, TODAY, 0)).isEqualTo(SetFlagOutcome.FLAG_CLEARED);
        assertThat(scoreboard.getMainCrosswordResult().getLookups()).isNull();
    }

    @Test
    void setLookupsNegativeReturnsInvalidValue() {
        assertThat(service.setLookups(USER_ID, TODAY, -1)).isEqualTo(SetFlagOutcome.INVALID_VALUE);
    }

    @Test
    void setLookupsReturnsNoMainCrosswordWhenAbsent() {
        assertThat(service.setLookups(USER_ID, TODAY, 1)).isEqualTo(SetFlagOutcome.NO_MAIN_CROSSWORD);
    }

    // ── toggleCheck ──────────────────────────────────────────────────────────

    @Test
    void toggleCheckSetsWhenNull() {
        setUpMainCrossword();
        assertThat(service.toggleCheck(USER_ID, TODAY)).isEqualTo(SetFlagOutcome.FLAG_SET);
        assertThat(scoreboard.getMainCrosswordResult().getCheckUsed()).isTrue();
    }

    @Test
    void toggleCheckClearsWhenTrue() {
        setUpMainCrossword();
        scoreboard.getMainCrosswordResult().setCheckUsed(true);
        assertThat(service.toggleCheck(USER_ID, TODAY)).isEqualTo(SetFlagOutcome.FLAG_CLEARED);
        assertThat(scoreboard.getMainCrosswordResult().getCheckUsed()).isFalse();
    }

    @Test
    void toggleCheckReturnsNoMainCrosswordWhenAbsent() {
        assertThat(service.toggleCheck(USER_ID, TODAY)).isEqualTo(SetFlagOutcome.NO_MAIN_CROSSWORD);
    }

    // ── Streak integration ─────────────────────────────────────────────────

    @Test
    void streakIsUpdatedWhenSaveSucceeds() {
        int expected = calendar.expectedWordle();
        WordleResult result = new WordleResult("raw", PERSON, null, expected, 3, true, false);

        assertThat(service.saveResult(CHANNEL, PERSON, USER_ID, result)).isEqualTo(SaveOutcome.SAVED);
        verify(streakService).updateStreak(eq(user), eq(result));
    }

    @Test
    void streakNotUpdatedOnAlreadySubmitted() {
        int expected = calendar.expectedWordle();
        WordleResult first = new WordleResult("raw", PERSON, null, expected, 3, true, false);
        scoreboard.setWordleResult(first);

        WordleResult second = new WordleResult("raw2", PERSON, null, expected, 4, true, false);
        assertThat(service.saveResult(CHANNEL, PERSON, USER_ID, second)).isEqualTo(SaveOutcome.ALREADY_SUBMITTED);
        verify(streakService, never()).updateStreak(any(), any());
    }

    @Test
    void streakNotUpdatedOnWrongPuzzleNumber() {
        WordleResult result = new WordleResult("raw", PERSON, null, 1, 3, true, false);

        assertThat(service.saveResult(CHANNEL, PERSON, USER_ID, result)).isEqualTo(SaveOutcome.WRONG_PUZZLE_NUMBER);
        verify(streakService, never()).updateStreak(any(), any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void setUpMainCrossword() {
        MainCrosswordResult main = new MainCrosswordResult("raw", PERSON, null, "15:00", 900, TODAY);
        scoreboard.setMainCrosswordResult(main);
    }
}

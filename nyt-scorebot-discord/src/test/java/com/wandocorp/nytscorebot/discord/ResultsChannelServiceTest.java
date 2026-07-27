package com.wandocorp.nytscorebot.discord;

import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties.ChannelConfig;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.service.CrosswordWinStreakService;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import com.wandocorp.nytscorebot.service.StreakService;
import com.wandocorp.nytscorebot.service.TripleCrownService;
import com.wandocorp.nytscorebot.service.WinStreakService;
import com.wandocorp.nytscorebot.service.scoreboard.ScoreboardRenderer;
import discord4j.common.util.Snowflake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class ResultsChannelServiceTest {

    private static final String RESULTS_CHANNEL_ID = "999";
    private static final String NAME1 = "Will";
    private static final String NAME2 = "Conor";

    private ScoreboardService scoreboardService;
    private ScoreboardRenderer scoreboardRenderer;
    private DiscordChannelProperties channelProperties;
    private MessageSlotWriter slotWriter;
    private TripleCrownService tripleCrownService;
    private ResultsChannelService service;

    @BeforeEach
    void setUp() {
        scoreboardService = mock(ScoreboardService.class);
        scoreboardRenderer = mock(ScoreboardRenderer.class);
        StreakService streakService = mock(StreakService.class);
        WinStreakService winStreakService = mock(WinStreakService.class);
        when(winStreakService.getStreaks(any())).thenReturn(Map.of());
        CrosswordWinStreakService crosswordWinStreakService = mock(CrosswordWinStreakService.class);
        when(crosswordWinStreakService.updateAll(any(), anyString(), any(), anyString(), any()))
                .thenReturn(Map.of());
        when(crosswordWinStreakService.computeOutcomes(any(), anyString(), any(), anyString()))
                .thenReturn(Map.of());
        tripleCrownService = mock(TripleCrownService.class);
        when(tripleCrownService.detect(any(), any(), anyString(), any(), anyString()))
                .thenReturn(java.util.Optional.empty());
        PuzzleCalendar puzzleCalendar = mock(PuzzleCalendar.class);
        when(puzzleCalendar.today()).thenReturn(LocalDate.now());
        slotWriter = mock(MessageSlotWriter.class);
        // Default: editOrPost returns the existing id (or a placeholder when null) so subscribers update map.
        when(slotWriter.editOrPost(any(Snowflake.class), any(), anyString()))
                .thenAnswer(inv -> {
                    Snowflake existing = inv.getArgument(1);
                    return Mono.just(existing != null ? existing : Snowflake.of("9999"));
                });
        when(slotWriter.editOrPost(any(Snowflake.class), isNull(), anyString()))
                .thenReturn(Mono.just(Snowflake.of("9999")));

        channelProperties = new DiscordChannelProperties();

        ChannelConfig c1 = new ChannelConfig();
        c1.setId("111");
        c1.setName(NAME1);
        c1.setUserId("u1");

        ChannelConfig c2 = new ChannelConfig();
        c2.setId("222");
        c2.setName(NAME2);
        c2.setUserId("u2");

        channelProperties.setChannels(List.of(c1, c2));
        channelProperties.setResultsChannelId(RESULTS_CHANNEL_ID);

        service = new ResultsChannelService(channelProperties, scoreboardService, scoreboardRenderer,
                streakService, winStreakService, crosswordWinStreakService, tripleCrownService, puzzleCalendar, slotWriter);
    }

    @Test
    void refreshNoOpWhenResultsChannelIdIsNull() {
        channelProperties.setResultsChannelId(null);
        service.refresh();
        verifyNoInteractions(scoreboardService);
    }

    @Test
    void refreshNoOpWhenResultsChannelIdIsBlank() {
        channelProperties.setResultsChannelId("   ");
        service.refresh();
        verifyNoInteractions(scoreboardService);
    }

    @Test
    void refreshNoOpWhenNotBothPlayersFinished() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(false);
        service.refresh();
        verifyNoInteractions(slotWriter);
        verifyNoInteractions(scoreboardRenderer);
    }

    @Test
    void refreshPostsFreshWhenNoExistingMessageId() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        setupRendered();

        service.refresh();

        verify(slotWriter, atLeastOnce()).editOrPost(eq(Snowflake.of(RESULTS_CHANNEL_ID)), isNull(),
                eq("```\nWordle stuff\n```"));
        // The returned id should have been stored under the slot.
        assertThat(service.getPostedMessageId("Wordle")).isEqualTo(Snowflake.of("9999"));
    }

    @Test
    void refreshEditsInPlaceWhenMessageIdKnown() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        setupRendered();

        Snowflake previousMsgId = Snowflake.of("11111");
        service.setPostedMessageId("Wordle", previousMsgId);

        service.refresh();

        verify(slotWriter).editOrPost(eq(Snowflake.of(RESULTS_CHANNEL_ID)), eq(previousMsgId),
                eq("```\nWordle stuff\n```"));
        // Existing id retained because helper returned it.
        assertThat(service.getPostedMessageId("Wordle")).isEqualTo(previousMsgId);
    }

    @Test
    void refreshUpdatesMapWhenHelperReturnsNewIdAfterFallback() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        setupRendered();

        Snowflake previousMsgId = Snowflake.of("11111");
        Snowflake newMsgId = Snowflake.of("22222");
        service.setPostedMessageId("Wordle", previousMsgId);

        // Helper returns a different (new) id, simulating an edit failure → fresh post fallback.
        when(slotWriter.editOrPost(any(Snowflake.class), eq(previousMsgId), anyString()))
                .thenReturn(Mono.just(newMsgId));

        service.refresh();

        assertThat(service.getPostedMessageId("Wordle")).isEqualTo(newMsgId);
    }

    @Test
    void refreshClearsTrackedIdsWhenDayHasRolledOver() {
        // Simulate yesterday's refresh having happened: a previous post id is tracked
        // and lastRefreshDate is yesterday (achieved by stubbing puzzleCalendar.today() differently).
        Snowflake yesterdayMsgId = Snowflake.of("11111");
        service.setPostedMessageId("Wordle", yesterdayMsgId);

        // First refresh: pretend today is yesterday so lastRefreshDate gets set to yesterday.
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate today = LocalDate.now();
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        setupRendered();

        // Reach into the puzzleCalendar mock used in setUp via the service's calendar.
        // We rebuild the service with a controllable calendar.
        PuzzleCalendar calendar = mock(PuzzleCalendar.class);
        when(calendar.today()).thenReturn(yesterday);
        StreakService streakService = mock(StreakService.class);
        WinStreakService winStreakService = mock(WinStreakService.class);
        when(winStreakService.getStreaks(any())).thenReturn(Map.of());
        CrosswordWinStreakService crosswordWinStreakService = mock(CrosswordWinStreakService.class);
        ResultsChannelService svc = new ResultsChannelService(channelProperties, scoreboardService,
                scoreboardRenderer, streakService, winStreakService, crosswordWinStreakService,
                mock(TripleCrownService.class), calendar, slotWriter);
        svc.setPostedMessageId("Wordle", yesterdayMsgId);

        svc.refresh(); // sets lastRefreshDate to yesterday
        org.mockito.Mockito.clearInvocations(slotWriter);

        // Second refresh on the new day: tracked id should be cleared, so a fresh post (id=null) is made.
        when(calendar.today()).thenReturn(today);
        svc.refresh();

        // The second editOrPost call for the "Wordle" slot should be invoked with a null prior id,
        // proving the stale yesterday id was discarded rather than edited.
        verify(slotWriter, atLeastOnce()).editOrPost(eq(Snowflake.of(RESULTS_CHANNEL_ID)), isNull(),
                eq("```\nWordle stuff\n```"));
        verify(slotWriter, never()).editOrPost(eq(Snowflake.of(RESULTS_CHANNEL_ID)), eq(yesterdayMsgId),
                eq("```\nWordle stuff\n```"));
    }

    private void setupScoreboards() {
        Scoreboard sb1 = new Scoreboard(new User("111", NAME1, "u1"), LocalDate.now());
        sb1.setFinished(true);
        Scoreboard sb2 = new Scoreboard(new User("222", NAME2, "u2"), LocalDate.now());
        sb2.setFinished(true);
        when(scoreboardService.getScoreboardsForDate(any())).thenReturn(List.of(sb1, sb2));
    }

    // ── refreshGame ──────────────────────────────────────────────────────────

    @Test
    void refreshGameNoOpWhenResultsChannelIdIsNull() {
        channelProperties.setResultsChannelId(null);
        service.refreshGame("Main");
        verifyNoInteractions(scoreboardService);
    }

    @Test
    void refreshGameNoOpWhenResultsChannelIdIsBlank() {
        channelProperties.setResultsChannelId("   ");
        service.refreshGame("Main");
        verifyNoInteractions(scoreboardService);
    }

    @Test
    void refreshGameNoOpWhenNotBothPlayersFinished() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(false);
        service.refreshGame("Main");
        verifyNoInteractions(slotWriter);
        verifyNoInteractions(scoreboardRenderer);
    }

    @Test
    void refreshGamePostsFreshWhenNoExistingMessage() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        when(scoreboardRenderer.renderByGameType(eq("Main"), any(), anyString(), any(), anyString(), any()))
                .thenReturn(java.util.Optional.of("```\nMain crossword\n```"));

        service.refreshGame("Main");

        verify(scoreboardRenderer).renderByGameType(eq("Main"), any(), eq(NAME1), any(), eq(NAME2), any());
        verify(slotWriter).editOrPost(eq(Snowflake.of(RESULTS_CHANNEL_ID)), isNull(),
                eq("```\nMain crossword\n```"));
    }

    @Test
    void refreshGameEditsInPlaceWhenExistingMessageId() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        when(scoreboardRenderer.renderByGameType(eq("Main"), any(), anyString(), any(), anyString(), any()))
                .thenReturn(java.util.Optional.of("```\nMain crossword\n```"));

        Snowflake previousMsgId = Snowflake.of("22222");
        service.setPostedMessageId("Main", previousMsgId);

        service.refreshGame("Main");

        verify(slotWriter).editOrPost(eq(Snowflake.of(RESULTS_CHANNEL_ID)), eq(previousMsgId),
                eq("```\nMain crossword\n```"));
    }

    @Test
    void refreshGameNoOpWhenRendererReturnsEmpty() {
        // Use a non-crossword game so the win streak summary path doesn't activate.
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        when(scoreboardRenderer.renderByGameType(eq("Wordle"), any(), anyString(), any(), anyString(), any()))
                .thenReturn(java.util.Optional.empty());

        service.refreshGame("Wordle");

        verify(slotWriter, never()).editOrPost(any(), any(), anyString());
    }

    // ── forceRefreshForDate ───────────────────────────────────────────────────

    @Test
    void forceRefreshForDatePostsEvenWhenNotBothPlayersFinished() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(false);
        setupScoreboards();
        setupRendered();
        LocalDate yesterday = LocalDate.now().minusDays(1);

        service.forceRefreshForDate(yesterday);

        verify(slotWriter, atLeastOnce()).editOrPost(eq(Snowflake.of(RESULTS_CHANNEL_ID)), isNull(),
                eq("```\nWordle stuff\n```"));
    }

    @Test
    void forceRefreshForDateSetsLastRefreshDate() {
        setupScoreboards();
        setupRendered();
        LocalDate yesterday = LocalDate.now().minusDays(1);

        service.forceRefreshForDate(yesterday);

        assertThat(service.hasPostedResultsForDate(yesterday)).isTrue();
        assertThat(service.hasPostedResultsForDate(LocalDate.now())).isFalse();
    }

    // ── hasPostedResultsForDate ───────────────────────────────────────────────

    @Test
    void hasPostedResultsForDateReturnsFalseInitially() {
        assertThat(service.hasPostedResultsForDate(LocalDate.now())).isFalse();
        assertThat(service.hasPostedResultsForDate(LocalDate.now().minusDays(1))).isFalse();
    }

    @Test
    void hasPostedResultsReturnsTrueAfterRefresh() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        setupRendered();

        service.refresh();

        assertThat(service.hasPostedResultsForDate(LocalDate.now())).isTrue();
    }

    private void setupRendered() {
        Map<String, String> rendered = new LinkedHashMap<>();
        rendered.put("Wordle", "```\nWordle stuff\n```");
        when(scoreboardRenderer.renderAll(any(), anyString(), any(), anyString(), any())).thenReturn(rendered);
    }

    // ── Triple crown ─────────────────────────────────────────────────────────

    private void crownGoesTo(String name) {
        when(tripleCrownService.detect(any(), any(), anyString(), any(), anyString()))
                .thenReturn(java.util.Optional.ofNullable(name));
    }

    private String crownMessage(String name) {
        return com.wandocorp.nytscorebot.BotText.TRIPLE_CROWN.formatted(name);
    }

    @Test
    void refreshPostsTheCelebrationWhenAPlayerSweeps() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        setupRendered();
        crownGoesTo(NAME1);

        service.refresh();

        verify(slotWriter).editOrPost(eq(Snowflake.of(RESULTS_CHANNEL_ID)), isNull(),
                eq(crownMessage(NAME1)));
        assertThat(service.getPostedMessageId(ResultsChannelService.TRIPLE_CROWN_SLOT)).isNotNull();
    }

    @Test
    void refreshPostsNoCelebrationWhenThereIsNoSweep() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        setupRendered();
        crownGoesTo(null);

        service.refresh();

        verify(slotWriter, never()).editOrPost(any(Snowflake.class), any(), contains("Triple Crown"));
        assertThat(service.getPostedMessageId(ResultsChannelService.TRIPLE_CROWN_SLOT)).isNull();
    }

    @Test
    void celebrationIsPostedAfterTheWinStreakSummary() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        setupRendered();
        crownGoesTo(NAME1);

        service.refresh();

        InOrder order = inOrder(slotWriter);
        order.verify(slotWriter).editOrPost(any(Snowflake.class), any(), eq("```\nWordle stuff\n```"));
        order.verify(slotWriter).editOrPost(any(Snowflake.class), any(),
                argThat(s -> s != null && !s.equals(crownMessage(NAME1)) && !s.contains("Wordle stuff")));
        order.verify(slotWriter).editOrPost(any(Snowflake.class), any(), eq(crownMessage(NAME1)));
    }

    @Test
    void celebrationIsNotRepostedWhenTheSameSweepStillStands() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        setupRendered();
        crownGoesTo(NAME1);

        service.refresh();
        org.mockito.Mockito.clearInvocations(slotWriter);
        service.refresh();

        verify(slotWriter, never()).editOrPost(any(Snowflake.class), any(), eq(crownMessage(NAME1)));
    }

    @Test
    void celebrationIsDeletedWhenTheSweepIsRevoked() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        setupRendered();
        when(slotWriter.delete(any(Snowflake.class), any(Snowflake.class))).thenReturn(Mono.empty());
        crownGoesTo(NAME1);
        service.refresh();

        // A /duo flag change breaks the sweep.
        crownGoesTo(null);
        service.refresh();

        verify(slotWriter).delete(eq(Snowflake.of(RESULTS_CHANNEL_ID)), eq(Snowflake.of("9999")));
        assertThat(service.getPostedMessageId(ResultsChannelService.TRIPLE_CROWN_SLOT)).isNull();
    }

    @Test
    void celebrationCanBeReEarnedAfterBeingRevoked() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        setupRendered();
        when(slotWriter.delete(any(Snowflake.class), any(Snowflake.class))).thenReturn(Mono.empty());

        crownGoesTo(NAME1);
        service.refresh();
        crownGoesTo(null);
        service.refresh();
        org.mockito.Mockito.clearInvocations(slotWriter);
        crownGoesTo(NAME1);
        service.refresh();

        verify(slotWriter).editOrPost(eq(Snowflake.of(RESULTS_CHANNEL_ID)), isNull(),
                eq(crownMessage(NAME1)));
    }

    @Test
    void celebrationIsReplacedWhenTheWinnerChanges() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        setupRendered();
        crownGoesTo(NAME1);
        service.refresh();

        crownGoesTo(NAME2);
        service.refresh();

        verify(slotWriter).editOrPost(any(Snowflake.class), any(), eq(crownMessage(NAME2)));
    }

    @Test
    void noDeleteIsAttemptedWhenNoCelebrationWasEverPosted() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        setupRendered();
        crownGoesTo(null);

        service.refresh();

        verify(slotWriter, never()).delete(any(Snowflake.class), any(Snowflake.class));
    }

    @Test
    void refreshTripleCrownForDatePostsWithoutTouchingTheBoards() {
        setupScoreboards();
        crownGoesTo(NAME1);

        service.refreshTripleCrownForDate(LocalDate.now().minusDays(1));

        verify(slotWriter).editOrPost(eq(Snowflake.of(RESULTS_CHANNEL_ID)), isNull(),
                eq(crownMessage(NAME1)));
        verify(scoreboardRenderer, never()).renderAll(any(), anyString(), any(), anyString(), any());
    }

    @Test
    void refreshTripleCrownForDateNoOpsWhenNoResultsChannelConfigured() {
        channelProperties.setResultsChannelId(null);
        crownGoesTo(NAME1);

        service.refreshTripleCrownForDate(LocalDate.now().minusDays(1));

        verifyNoInteractions(slotWriter);
    }

    @Test
    void dayRolloverClearsTheCrownSoTheSamePlayerCanWinAgainTomorrow() {
        LocalDate day1 = LocalDate.of(2026, 5, 17);
        PuzzleCalendar calendar = mock(PuzzleCalendar.class);
        when(calendar.today()).thenReturn(day1);
        StreakService streakService = mock(StreakService.class);
        WinStreakService winStreakService = mock(WinStreakService.class);
        when(winStreakService.getStreaks(any())).thenReturn(Map.of());
        CrosswordWinStreakService crosswordWinStreakService = mock(CrosswordWinStreakService.class);
        when(crosswordWinStreakService.updateAll(any(), anyString(), any(), anyString(), any()))
                .thenReturn(Map.of());
        ResultsChannelService svc = new ResultsChannelService(channelProperties, scoreboardService,
                scoreboardRenderer, streakService, winStreakService, crosswordWinStreakService,
                tripleCrownService, calendar, slotWriter);

        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        setupRendered();
        crownGoesTo(NAME1);

        svc.refresh();
        when(calendar.today()).thenReturn(day1.plusDays(1));
        org.mockito.Mockito.clearInvocations(slotWriter);
        svc.refresh();

        verify(slotWriter).editOrPost(eq(Snowflake.of(RESULTS_CHANNEL_ID)), isNull(),
                eq(crownMessage(NAME1)));
    }

    // ── Player resolution is by id, not display name ─────────────────────────

    @Test
    void scoreboardsResolveWhenThePersistedNameDiffersFromTheConfiguredName() {
        // The persisted app_user.name is stale relative to discord.channels[n].name.
        Scoreboard sb1 = new Scoreboard(new User("111", "William", "u1"), LocalDate.now());
        sb1.setFinished(true);
        Scoreboard sb2 = new Scoreboard(new User("222", "conor", "u2"), LocalDate.now());
        sb2.setFinished(true);
        when(scoreboardService.getScoreboardsForDate(any())).thenReturn(List.of(sb1, sb2));
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupRendered();

        service.refresh();

        verify(scoreboardRenderer).renderAll(eq(sb1), eq(NAME1), eq(sb2), eq(NAME2), any());
    }

    @Test
    void scoreboardsResolveByDiscordUserIdWhenTheChannelIdIsUnknown() {
        Scoreboard sb1 = new Scoreboard(new User("legacy-channel", "whoever", "u1"), LocalDate.now());
        sb1.setFinished(true);
        Scoreboard sb2 = new Scoreboard(new User("other-legacy", "whoever2", "u2"), LocalDate.now());
        sb2.setFinished(true);
        when(scoreboardService.getScoreboardsForDate(any())).thenReturn(List.of(sb1, sb2));
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupRendered();

        service.refresh();

        verify(scoreboardRenderer).renderAll(eq(sb1), eq(NAME1), eq(sb2), eq(NAME2), any());
    }

    @Test
    void unresolvableScoreboardsRenderAsAbsentRatherThanBeingMisattributed() {
        Scoreboard stranger = new Scoreboard(new User("nope", NAME1, "nope"), LocalDate.now());
        when(scoreboardService.getScoreboardsForDate(any())).thenReturn(List.of(stranger));
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupRendered();

        service.refresh();

        // Matching on the display name would have wrongly bound this scoreboard to player 1.
        verify(scoreboardRenderer).renderAll(isNull(), eq(NAME1), isNull(), eq(NAME2), any());
    }
}

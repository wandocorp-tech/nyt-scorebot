package com.wandocorp.nytscorebot.discord;

import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties.ChannelConfig;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.service.CrosswordWinStreakService;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import com.wandocorp.nytscorebot.service.StreakService;
import com.wandocorp.nytscorebot.service.WinStreakService;
import com.wandocorp.nytscorebot.service.scoreboard.ScoreboardRenderer;
import discord4j.common.util.Snowflake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class ResultsChannelServiceTest {

    private static final String RESULTS_CHANNEL_ID = "999";
    private static final String NAME1 = "William";
    private static final String NAME2 = "Conor";

    private ScoreboardService scoreboardService;
    private ScoreboardRenderer scoreboardRenderer;
    private DiscordChannelProperties channelProperties;
    private MessageSlotWriter slotWriter;
    private ResultsChannelService service;

    @BeforeEach
    void setUp() {
        scoreboardService = mock(ScoreboardService.class);
        scoreboardRenderer = mock(ScoreboardRenderer.class);
        StreakService streakService = mock(StreakService.class);
        WinStreakService winStreakService = mock(WinStreakService.class);
        when(winStreakService.getStreaks(any())).thenReturn(Map.of());
        CrosswordWinStreakService crosswordWinStreakService = mock(CrosswordWinStreakService.class);
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
                streakService, winStreakService, crosswordWinStreakService, puzzleCalendar, slotWriter);
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
}

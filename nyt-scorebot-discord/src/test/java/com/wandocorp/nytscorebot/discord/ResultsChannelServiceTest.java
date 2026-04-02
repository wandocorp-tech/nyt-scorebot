package com.wandocorp.nytscorebot.discord;

import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties.ChannelConfig;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import com.wandocorp.nytscorebot.service.StreakService;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.service.scoreboard.ScoreboardRenderer;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
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
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
class ResultsChannelServiceTest {

    private static final String RESULTS_CHANNEL_ID = "999";
    private static final String NAME1 = "William";
    private static final String NAME2 = "Conor";

    private GatewayDiscordClient client;
    private ScoreboardService scoreboardService;
    private ScoreboardRenderer scoreboardRenderer;
    private StreakService streakService;
    private DiscordChannelProperties channelProperties;
    private ResultsChannelService service;

    @BeforeEach
    void setUp() {
        client = mock(GatewayDiscordClient.class);
        scoreboardService = mock(ScoreboardService.class);
        scoreboardRenderer = mock(ScoreboardRenderer.class);
        streakService = mock(StreakService.class);

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

        service = new ResultsChannelService(client, channelProperties, scoreboardService, scoreboardRenderer, streakService);
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
        verifyNoInteractions(client);
        verifyNoInteractions(scoreboardRenderer);
    }

    @Test
    void refreshCallsGetChannelByIdWhenBothDone() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        setupRendered();

        MessageChannel channel = mock(MessageChannel.class);
        when(client.getChannelById(any(Snowflake.class))).thenReturn(Mono.just(channel));

        service.refresh();

        verify(client).getChannelById(Snowflake.of(RESULTS_CHANNEL_ID));
        verify(channel).createMessage("```\nWordle stuff\n```");
    }

    @Test
    void refreshCallsGetMessageByIdOnSecondCallWhenMessageIdKnown() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        setupRendered();

        // Pre-populate postedMessageIds to simulate a prior call having stored a message ID
        Snowflake previousMsgId = Snowflake.of("11111");
        service.setPostedMessageId("Wordle", previousMsgId);

        Message existingMsg = mock(Message.class);
        when(existingMsg.delete()).thenReturn(Mono.empty());
        when(client.getMessageById(any(Snowflake.class), any(Snowflake.class)))
                .thenReturn(Mono.just(existingMsg));

        MessageChannel channel = mock(MessageChannel.class);
        when(client.getChannelById(any(Snowflake.class))).thenReturn(Mono.just(channel));

        service.refresh();

        verify(client).getMessageById(Snowflake.of(RESULTS_CHANNEL_ID), previousMsgId);
        verify(existingMsg).delete();
        verify(channel).createMessage("```\nWordle stuff\n```");
    }

    private void setupScoreboards() {
        Scoreboard sb1 = new Scoreboard(new User("111", NAME1, "u1"), LocalDate.now());
        sb1.setFinished(true);
        Scoreboard sb2 = new Scoreboard(new User("222", NAME2, "u2"), LocalDate.now());
        sb2.setFinished(true);
        when(scoreboardService.getTodayScoreboards()).thenReturn(List.of(sb1, sb2));
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
        verifyNoInteractions(client);
        verifyNoInteractions(scoreboardRenderer);
    }

    @Test
    void refreshGamePostsNewMessageWhenNoExistingMessage() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        when(scoreboardRenderer.renderByGameType(eq("Main"), any(), anyString(), any(), anyString(), any()))
                .thenReturn(java.util.Optional.of("```\nMain crossword\n```"));

        MessageChannel channel = mock(MessageChannel.class);
        when(client.getChannelById(any(Snowflake.class))).thenReturn(Mono.just(channel));

        service.refreshGame("Main");

        verify(scoreboardRenderer).renderByGameType(eq("Main"), any(), eq(NAME1), any(), eq(NAME2), any());
        verify(channel).createMessage("```\nMain crossword\n```");
    }

    @Test
    void refreshGameDeletesAndRepostsWhenExistingMessageId() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        when(scoreboardRenderer.renderByGameType(eq("Main"), any(), anyString(), any(), anyString(), any()))
                .thenReturn(java.util.Optional.of("```\nMain crossword\n```"));

        Snowflake previousMsgId = Snowflake.of("22222");
        service.setPostedMessageId("Main", previousMsgId);

        Message existingMsg = mock(Message.class);
        when(existingMsg.delete()).thenReturn(Mono.empty());
        when(client.getMessageById(any(Snowflake.class), any(Snowflake.class)))
                .thenReturn(Mono.just(existingMsg));

        MessageChannel channel = mock(MessageChannel.class);
        when(client.getChannelById(any(Snowflake.class))).thenReturn(Mono.just(channel));

        service.refreshGame("Main");

        verify(client).getMessageById(Snowflake.of(RESULTS_CHANNEL_ID), previousMsgId);
        verify(existingMsg).delete();
        verify(channel).createMessage("```\nMain crossword\n```");
    }

    @Test
    void refreshGameNoOpWhenRendererReturnsEmpty() {
        when(scoreboardService.areBothPlayersFinishedToday()).thenReturn(true);
        setupScoreboards();
        when(scoreboardRenderer.renderByGameType(eq("Main"), any(), anyString(), any(), anyString(), any()))
                .thenReturn(java.util.Optional.empty());

        service.refreshGame("Main");

        verifyNoInteractions(client);
    }

    private void setupRendered() {
        Map<String, String> rendered = new LinkedHashMap<>();
        rendered.put("Wordle", "```\nWordle stuff\n```");
        when(scoreboardRenderer.renderAll(any(), anyString(), any(), anyString(), any())).thenReturn(rendered);
    }

}

package com.wandocorp.nytscorebot.listener.command;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties.ChannelConfig;
import com.wandocorp.nytscorebot.discord.ResultsChannelService;
import com.wandocorp.nytscorebot.discord.StatusChannelService;
import com.wandocorp.nytscorebot.service.MarkFinishedOutcome;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.Interaction;
import discord4j.core.object.entity.User;
import discord4j.core.spec.InteractionApplicationCommandCallbackReplyMono;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.wandocorp.nytscorebot.listener.command.FinishedCommandHandler.replyMessageFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinishedCommandHandlerTest {

    private static final String DISCORD_USER_ID = "123456789";
    private static final String PLAYER_NAME = "TestPlayer";
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 23);

    private ScoreboardService scoreboardService;
    private PuzzleCalendar puzzleCalendar;
    private StatusChannelService statusChannelService;
    private ResultsChannelService resultsChannelService;
    private FinishedCommandHandler handler;

    @BeforeEach
    void setUp() {
        scoreboardService = mock(ScoreboardService.class);
        puzzleCalendar = mock(PuzzleCalendar.class);
        statusChannelService = mock(StatusChannelService.class);
        resultsChannelService = mock(ResultsChannelService.class);
        when(puzzleCalendar.today()).thenReturn(TODAY);

        DiscordChannelProperties channelProperties = new DiscordChannelProperties();
        ChannelConfig ch = new ChannelConfig();
        ch.setId("ch1");
        ch.setName(PLAYER_NAME);
        ch.setUserId(DISCORD_USER_ID);
        channelProperties.setChannels(List.of(ch));

        handler = new FinishedCommandHandler(scoreboardService, puzzleCalendar, channelProperties,
                statusChannelService, resultsChannelService);
    }

    @Test
    void commandNameIsFinished() {
        assertThat(handler.commandName()).isEqualTo(BotText.CMD_FINISHED);
    }

    @Test
    void markedFinishedReplyContainsConfirmation() {
        assertThat(replyMessageFor(MarkFinishedOutcome.MARKED_FINISHED)).contains("marked as finished");
    }

    @Test
    void alreadyFinishedReplyIndicatesAlreadyDone() {
        assertThat(replyMessageFor(MarkFinishedOutcome.ALREADY_FINISHED)).contains("already marked as finished");
    }

    @Test
    void noScoreboardReplyIndicatesNoResults() {
        assertThat(replyMessageFor(MarkFinishedOutcome.NO_SCOREBOARD_FOR_DATE)).contains("haven't submitted");
    }

    @Test
    void userNotFoundReplyIndicatesNotTracked() {
        assertThat(replyMessageFor(MarkFinishedOutcome.USER_NOT_FOUND)).contains("not a tracked user");
    }

    @Test
    void handleCallsMarkFinishedWithCorrectArgs() {
        when(scoreboardService.markFinished(DISCORD_USER_ID, TODAY))
                .thenReturn(MarkFinishedOutcome.MARKED_FINISHED);

        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID);
        handler.handle(event).subscribe();

        verify(scoreboardService).markFinished(eq(DISCORD_USER_ID), eq(TODAY));
    }

    @Test
    void handleRepliesEphemerallyOnSuccess() {
        when(scoreboardService.markFinished(any(), any())).thenReturn(MarkFinishedOutcome.MARKED_FINISHED);

        InteractionApplicationCommandCallbackReplyMono replySpec =
                mock(InteractionApplicationCommandCallbackReplyMono.class);
        InteractionApplicationCommandCallbackReplyMono withEphemeralSpec =
                mock(InteractionApplicationCommandCallbackReplyMono.class);
        InteractionApplicationCommandCallbackReplyMono withContentSpec =
                mock(InteractionApplicationCommandCallbackReplyMono.class);

        ChatInputInteractionEvent event = buildEventWithReplyMock(DISCORD_USER_ID, replySpec);
        doReturn(withEphemeralSpec).when(replySpec).withEphemeral(true);
        doReturn(withContentSpec).when(withEphemeralSpec).withContent((String) any());

        handler.handle(event).subscribe();

        verify(replySpec).withEphemeral(true);
        verify(withEphemeralSpec).withContent((String) argThat(s -> s.toString().contains("marked as finished")));
    }

    @Test
    void handleRefreshesOnMarkedFinished() {
        when(scoreboardService.markFinished(any(), any())).thenReturn(MarkFinishedOutcome.MARKED_FINISHED);

        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID);
        handler.handle(event).subscribe();

        verify(statusChannelService).refresh(anyString());
        verify(resultsChannelService).refresh();
    }

    @Test
    void handleRefreshesOnAlreadyFinished() {
        when(scoreboardService.markFinished(any(), any())).thenReturn(MarkFinishedOutcome.ALREADY_FINISHED);

        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID);
        handler.handle(event).subscribe();

        verify(statusChannelService).refresh(anyString());
        verify(resultsChannelService).refresh();
    }

    @Test
    void handleDoesNotRefreshOnNoScoreboard() {
        when(scoreboardService.markFinished(any(), any())).thenReturn(MarkFinishedOutcome.NO_SCOREBOARD_FOR_DATE);

        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID);
        handler.handle(event).subscribe();

        verify(statusChannelService, never()).refresh(anyString());
        verify(resultsChannelService, never()).refresh();
    }

    @Test
    void handleDoesNotRefreshOnUserNotFound() {
        when(scoreboardService.markFinished(any(), any())).thenReturn(MarkFinishedOutcome.USER_NOT_FOUND);

        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID);
        handler.handle(event).subscribe();

        verify(statusChannelService, never()).refresh(anyString());
        verify(resultsChannelService, never()).refresh();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ChatInputInteractionEvent buildEvent(String userId) {
        InteractionApplicationCommandCallbackReplyMono replySpec =
                mock(InteractionApplicationCommandCallbackReplyMono.class);
        return buildEventWithReplyMock(userId, replySpec);
    }

    private ChatInputInteractionEvent buildEventWithReplyMock(
            String userId, InteractionApplicationCommandCallbackReplyMono replySpec) {
        User discordUser = mock(User.class);
        doReturn(Snowflake.of(userId)).when(discordUser).getId();

        Interaction interaction = mock(Interaction.class);
        doReturn(discordUser).when(interaction).getUser();

        ChatInputInteractionEvent event = mock(ChatInputInteractionEvent.class);
        doReturn("finished").when(event).getCommandName();
        doReturn(interaction).when(event).getInteraction();
        doReturn(replySpec).when(event).reply();

        InteractionApplicationCommandCallbackReplyMono withEphemeralSpec =
                mock(InteractionApplicationCommandCallbackReplyMono.class);
        InteractionApplicationCommandCallbackReplyMono withContentSpec =
                mock(InteractionApplicationCommandCallbackReplyMono.class);
        doReturn(withEphemeralSpec).when(replySpec).withEphemeral(true);
        doReturn(withContentSpec).when(withEphemeralSpec).withContent((String) any());

        return event;
    }
}

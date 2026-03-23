package com.wandocorp.nytscorebot.listener;

import com.wandocorp.nytscorebot.service.MarkCompleteOutcome;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.Interaction;
import discord4j.core.object.entity.User;
import discord4j.core.spec.InteractionApplicationCommandCallbackReplyMono;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.LocalDate;

import static com.wandocorp.nytscorebot.listener.SlashCommandListener.replyMessageFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SlashCommandListenerTest {

    private static final String DISCORD_USER_ID = "123456789";
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 23);

    private ScoreboardService scoreboardService;
    private PuzzleCalendar puzzleCalendar;
    private SlashCommandListener listener;

    @BeforeEach
    void setUp() {
        scoreboardService = mock(ScoreboardService.class);
        puzzleCalendar = mock(PuzzleCalendar.class);
        when(puzzleCalendar.today()).thenReturn(TODAY);

        // GatewayDiscordClient only used in subscribe() — not needed in unit tests
        listener = new SlashCommandListener(null, scoreboardService, puzzleCalendar);
    }

    // ── replyMessageFor ───────────────────────────────────────────────────────

    @Test
    void markedCompleteReplyContainsConfirmation() {
        assertThat(replyMessageFor(MarkCompleteOutcome.MARKED_COMPLETE)).contains("marked as complete");
    }

    @Test
    void alreadyCompleteReplyIndicatesAlreadyDone() {
        assertThat(replyMessageFor(MarkCompleteOutcome.ALREADY_COMPLETE)).contains("already marked complete");
    }

    @Test
    void noScoreboardReplyIndicatesNoResults() {
        assertThat(replyMessageFor(MarkCompleteOutcome.NO_SCOREBOARD_FOR_DATE)).contains("haven't submitted");
    }

    @Test
    void userNotFoundReplyIndicatesNotTracked() {
        assertThat(replyMessageFor(MarkCompleteOutcome.USER_NOT_FOUND)).contains("not a tracked user");
    }

    // ── handleFinished ────────────────────────────────────────────────────────

    @Test
    void handleFinishedCallsMarkCompleteWithCorrectArgs() {
        when(scoreboardService.markComplete(DISCORD_USER_ID, TODAY))
                .thenReturn(MarkCompleteOutcome.MARKED_COMPLETE);

        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID);
        listener.handleFinished(event).subscribe();

        verify(scoreboardService).markComplete(eq(DISCORD_USER_ID), eq(TODAY));
    }

    @Test
    void handleFinishedRepliesEphemerallyOnSuccess() {
        when(scoreboardService.markComplete(any(), any())).thenReturn(MarkCompleteOutcome.MARKED_COMPLETE);

        InteractionApplicationCommandCallbackReplyMono replySpec =
                mock(InteractionApplicationCommandCallbackReplyMono.class);
        InteractionApplicationCommandCallbackReplyMono withEphemeralSpec =
                mock(InteractionApplicationCommandCallbackReplyMono.class);
        InteractionApplicationCommandCallbackReplyMono withContentSpec =
                mock(InteractionApplicationCommandCallbackReplyMono.class);

        ChatInputInteractionEvent event = buildEventWithReplyMock(DISCORD_USER_ID, replySpec);
        doReturn(withEphemeralSpec).when(replySpec).withEphemeral(true);
        doReturn(withContentSpec).when(withEphemeralSpec).withContent((String) any());

        listener.handleFinished(event).subscribe();

        verify(replySpec).withEphemeral(true);
        verify(withEphemeralSpec).withContent((String) argThat(s -> s.toString().contains("marked as complete")));
    }

    // ── listenToEvents filter ─────────────────────────────────────────────────

    @Test
    void nonFinishedCommandIsIgnored() {
        ChatInputInteractionEvent event = mock(ChatInputInteractionEvent.class);
        doReturn("leaderboard").when(event).getCommandName();

        listener.listenToEvents(Flux.just(event));

        verify(scoreboardService, never()).markComplete(any(), any());
    }

    @Test
    void finishedCommandIsHandled() {
        when(scoreboardService.markComplete(any(), any())).thenReturn(MarkCompleteOutcome.MARKED_COMPLETE);

        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID);
        listener.listenToEvents(Flux.just(event));

        verify(scoreboardService).markComplete(eq(DISCORD_USER_ID), eq(TODAY));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a mocked {@link ChatInputInteractionEvent} for the {@code /finished} command
     * with a fully wired reply chain: reply() → withEphemeral(true) → withContent(any()).
     */
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

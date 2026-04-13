package com.wandocorp.nytscorebot.listener;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties.ChannelConfig;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.repository.UserRepository;
import com.wandocorp.nytscorebot.service.MarkFinishedOutcome;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.service.SetFlagOutcome;
import com.wandocorp.nytscorebot.service.StreakService;
import com.wandocorp.nytscorebot.discord.ResultsChannelService;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import com.wandocorp.nytscorebot.discord.StatusChannelService;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.Interaction;
import discord4j.core.object.entity.User;
import discord4j.core.spec.InteractionApplicationCommandCallbackReplyMono;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.wandocorp.nytscorebot.listener.SlashCommandListener.replyMessageFor;
import static com.wandocorp.nytscorebot.listener.SlashCommandListener.flagReplyFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SlashCommandListenerTest {

    private static final String DISCORD_USER_ID = "123456789";
    private static final String PLAYER_NAME     = "TestPlayer";
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 23);

    private ScoreboardService scoreboardService;
    private StreakService streakService;
    private UserRepository userRepository;
    private PuzzleCalendar puzzleCalendar;
    private StatusChannelService statusChannelService;
    private ResultsChannelService resultsChannelService;
    private DiscordChannelProperties channelProperties;
    private SlashCommandListener listener;

    @BeforeEach
    void setUp() {
        scoreboardService = mock(ScoreboardService.class);
        streakService = mock(StreakService.class);
        userRepository = mock(UserRepository.class);
        puzzleCalendar = mock(PuzzleCalendar.class);
        statusChannelService = mock(StatusChannelService.class);
        resultsChannelService = mock(ResultsChannelService.class);
        when(puzzleCalendar.today()).thenReturn(TODAY);

        channelProperties = new DiscordChannelProperties();
        ChannelConfig ch = new ChannelConfig();
        ch.setId("ch1");
        ch.setName(PLAYER_NAME);
        ch.setUserId(DISCORD_USER_ID);
        channelProperties.setChannels(List.of(ch));

        listener = new SlashCommandListener(null, scoreboardService, streakService, userRepository, puzzleCalendar, statusChannelService, resultsChannelService, channelProperties);
    }

    // ── replyMessageFor ───────────────────────────────────────────────────────

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

    // ── handleFinished ────────────────────────────────────────────────────────

    @Test
    void handleFinishedCallsMarkFinishedWithCorrectArgs() {
        when(scoreboardService.markFinished(DISCORD_USER_ID, TODAY))
                .thenReturn(MarkFinishedOutcome.MARKED_FINISHED);

        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID);
        listener.handleFinished(event).subscribe();

        verify(scoreboardService).markFinished(eq(DISCORD_USER_ID), eq(TODAY));
    }

    @Test
    void handleFinishedRepliesEphemerallyOnSuccess() {
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

        listener.handleFinished(event).subscribe();

        verify(replySpec).withEphemeral(true);
        verify(withEphemeralSpec).withContent((String) argThat(s -> s.toString().contains("marked as finished")));
    }

    // ── listenToEvents filter ─────────────────────────────────────────────────

    @Test
    void handleFinishedCallsRefreshOnMarkedFinished() {
        when(scoreboardService.markFinished(any(), any())).thenReturn(MarkFinishedOutcome.MARKED_FINISHED);

        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID);
        listener.handleFinished(event).subscribe();

        verify(statusChannelService).refresh(anyString());
    }

    @Test
    void nonFinishedCommandIsIgnored() {
        ChatInputInteractionEvent event = mock(ChatInputInteractionEvent.class);
        doReturn("leaderboard").when(event).getCommandName();

        listener.listenToEvents(Flux.just(event));

        verify(scoreboardService, never()).markFinished(any(), any());
    }

    @Test
    void finishedCommandIsHandled() {
        when(scoreboardService.markFinished(any(), any())).thenReturn(MarkFinishedOutcome.MARKED_FINISHED);

        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID, "finished");
        listener.listenToEvents(Flux.just(event));

        verify(scoreboardService).markFinished(eq(DISCORD_USER_ID), eq(TODAY));
    }

    // ── Flag command reply mapping ────────────────────────────────────────────

    @Test
    void flagReplyForFlagSetReturnsSetMessage() {
        assertThat(flagReplyFor(BotText.MSG_DUO_SET, BotText.MSG_DUO_CLEARED, SetFlagOutcome.FLAG_SET))
                .isEqualTo(BotText.MSG_DUO_SET);
    }

    @Test
    void flagReplyForFlagClearedReturnsClearedMessage() {
        assertThat(flagReplyFor(BotText.MSG_CHECK_SET, BotText.MSG_CHECK_CLEARED, SetFlagOutcome.FLAG_CLEARED))
                .isEqualTo(BotText.MSG_CHECK_CLEARED);
    }

    @Test
    void flagReplyForNoMainCrossword() {
        assertThat(flagReplyFor(BotText.MSG_DUO_SET, BotText.MSG_DUO_CLEARED, SetFlagOutcome.NO_MAIN_CROSSWORD))
                .isEqualTo(BotText.MSG_NO_MAIN_CROSSWORD);
    }

    @Test
    void flagReplyForNoScoreboard() {
        assertThat(flagReplyFor(BotText.MSG_DUO_SET, BotText.MSG_DUO_CLEARED, SetFlagOutcome.NO_SCOREBOARD_FOR_DATE))
                .isEqualTo(BotText.MSG_NO_SCOREBOARD_TODAY);
    }

    @Test
    void flagReplyForUserNotFound() {
        assertThat(flagReplyFor(BotText.MSG_DUO_SET, BotText.MSG_DUO_CLEARED, SetFlagOutcome.USER_NOT_FOUND))
                .isEqualTo(BotText.MSG_USER_NOT_FOUND);
    }

    @Test
    void flagReplyForInvalidValue() {
        assertThat(flagReplyFor(BotText.MSG_DUO_SET, BotText.MSG_DUO_CLEARED, SetFlagOutcome.INVALID_VALUE))
                .isEqualTo(BotText.MSG_INVALID_VALUE);
    }

    // ── handleDuo ─────────────────────────────────────────────────────────────

    @Test
    void handleDuoCallsToggleDuo() {
        when(scoreboardService.toggleDuo(DISCORD_USER_ID, TODAY)).thenReturn(SetFlagOutcome.FLAG_SET);
        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID, "duo");
        listener.handleDuo(event).subscribe();
        verify(scoreboardService).toggleDuo(eq(DISCORD_USER_ID), eq(TODAY));
    }

    @Test
    void handleDuoRefreshesMainCrosswordOnSuccess() {
        when(scoreboardService.toggleDuo(any(), any())).thenReturn(SetFlagOutcome.FLAG_SET);
        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID, "duo");
        listener.handleDuo(event).subscribe();
        verify(resultsChannelService).refreshGame(BotText.GAME_LABEL_MAIN);
        verify(statusChannelService).refresh(anyString());
    }

    @Test
    void handleDuoDoesNotRefreshOnNoMainCrossword() {
        when(scoreboardService.toggleDuo(any(), any())).thenReturn(SetFlagOutcome.NO_MAIN_CROSSWORD);
        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID, "duo");
        listener.handleDuo(event).subscribe();
        verify(resultsChannelService, never()).refreshGame(any());
    }

    // ── handleCheck ───────────────────────────────────────────────────────────

    @Test
    void handleCheckCallsToggleCheck() {
        when(scoreboardService.toggleCheck(DISCORD_USER_ID, TODAY)).thenReturn(SetFlagOutcome.FLAG_SET);
        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID, "check");
        listener.handleCheck(event).subscribe();
        verify(scoreboardService).toggleCheck(eq(DISCORD_USER_ID), eq(TODAY));
    }

    @Test
    void handleCheckRefreshesMainCrosswordOnSuccess() {
        when(scoreboardService.toggleCheck(any(), any())).thenReturn(SetFlagOutcome.FLAG_CLEARED);
        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID, "check");
        listener.handleCheck(event).subscribe();
        verify(resultsChannelService).refreshGame(BotText.GAME_LABEL_MAIN);
    }

    // ── handleLookups ─────────────────────────────────────────────────────────

    @Test
    void handleLookupsCallsSetLookups() {
        when(scoreboardService.setLookups(DISCORD_USER_ID, TODAY, 3)).thenReturn(SetFlagOutcome.FLAG_SET);
        ChatInputInteractionEvent event = buildLookupsEvent(DISCORD_USER_ID, 3);
        listener.handleLookups(event).subscribe();
        verify(scoreboardService).setLookups(eq(DISCORD_USER_ID), eq(TODAY), eq(3));
    }

    @Test
    void handleLookupsRefreshesMainCrosswordOnSuccess() {
        when(scoreboardService.setLookups(any(), any(), eq(2))).thenReturn(SetFlagOutcome.FLAG_SET);
        ChatInputInteractionEvent event = buildLookupsEvent(DISCORD_USER_ID, 2);
        listener.handleLookups(event).subscribe();
        verify(resultsChannelService).refreshGame(BotText.GAME_LABEL_MAIN);
    }

    @Test
    void handleLookupsDoesNotRefreshOnInvalidValue() {
        when(scoreboardService.setLookups(any(), any(), eq(-1))).thenReturn(SetFlagOutcome.INVALID_VALUE);
        ChatInputInteractionEvent event = buildLookupsEvent(DISCORD_USER_ID, -1);
        listener.handleLookups(event).subscribe();
        verify(resultsChannelService, never()).refreshGame(any());
    }

    @Test
    void handleLookupsRefreshesOnFlagCleared() {
        when(scoreboardService.setLookups(any(), any(), eq(0))).thenReturn(SetFlagOutcome.FLAG_CLEARED);
        ChatInputInteractionEvent event = buildLookupsEvent(DISCORD_USER_ID, 0);
        listener.handleLookups(event).subscribe();
        verify(resultsChannelService).refreshGame(BotText.GAME_LABEL_MAIN);
        verify(statusChannelService).refresh(anyString());
    }

    @Test
    void handleLookupsDoesNotRefreshOnNoScoreboard() {
        when(scoreboardService.setLookups(any(), any(), eq(1))).thenReturn(SetFlagOutcome.NO_SCOREBOARD_FOR_DATE);
        ChatInputInteractionEvent event = buildLookupsEvent(DISCORD_USER_ID, 1);
        listener.handleLookups(event).subscribe();
        verify(resultsChannelService, never()).refreshGame(any());
    }

    @Test
    void handleLookupsDoesNotRefreshOnUserNotFound() {
        when(scoreboardService.setLookups(any(), any(), eq(1))).thenReturn(SetFlagOutcome.USER_NOT_FOUND);
        ChatInputInteractionEvent event = buildLookupsEvent(DISCORD_USER_ID, 1);
        listener.handleLookups(event).subscribe();
        verify(resultsChannelService, never()).refreshGame(any());
    }

    // ── handleFinished edge cases ─────────────────────────────────────────────

    @Test
    void handleFinishedRefreshesOnAlreadyFinished() {
        when(scoreboardService.markFinished(any(), any())).thenReturn(MarkFinishedOutcome.ALREADY_FINISHED);
        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID);
        listener.handleFinished(event).subscribe();
        verify(statusChannelService).refresh(anyString());
        verify(resultsChannelService).refresh();
    }

    @Test
    void handleFinishedDoesNotRefreshOnNoScoreboard() {
        when(scoreboardService.markFinished(any(), any())).thenReturn(MarkFinishedOutcome.NO_SCOREBOARD_FOR_DATE);
        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID);
        listener.handleFinished(event).subscribe();
        verify(statusChannelService, never()).refresh(anyString());
        verify(resultsChannelService, never()).refresh();
    }

    @Test
    void handleFinishedDoesNotRefreshOnUserNotFound() {
        when(scoreboardService.markFinished(any(), any())).thenReturn(MarkFinishedOutcome.USER_NOT_FOUND);
        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID);
        listener.handleFinished(event).subscribe();
        verify(statusChannelService, never()).refresh(anyString());
        verify(resultsChannelService, never()).refresh();
    }

    // ── listenToEvents dispatches new commands ────────────────────────────────

    @Test
    void duoCommandIsDispatchedViaListenToEvents() {
        when(scoreboardService.toggleDuo(any(), any())).thenReturn(SetFlagOutcome.FLAG_SET);
        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID, "duo");
        listener.listenToEvents(Flux.just(event));
        verify(scoreboardService).toggleDuo(eq(DISCORD_USER_ID), eq(TODAY));
    }

    @Test
    void checkCommandIsDispatchedViaListenToEvents() {
        when(scoreboardService.toggleCheck(any(), any())).thenReturn(SetFlagOutcome.FLAG_SET);
        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID, "check");
        listener.listenToEvents(Flux.just(event));
        verify(scoreboardService).toggleCheck(eq(DISCORD_USER_ID), eq(TODAY));
    }

    @Test
    void lookupsCommandIsDispatchedViaListenToEvents() {
        when(scoreboardService.setLookups(any(), any(), eq(5))).thenReturn(SetFlagOutcome.FLAG_SET);
        ChatInputInteractionEvent event = buildLookupsEvent(DISCORD_USER_ID, 5);
        listener.listenToEvents(Flux.just(event));
        verify(scoreboardService).setLookups(eq(DISCORD_USER_ID), eq(TODAY), eq(5));
    }

    // ── handleStreak ────────────────────────────────────────────────────────

    @Test
    void handleStreakSetsStreakSuccessfully() {
        com.wandocorp.nytscorebot.entity.User dbUser = mock(com.wandocorp.nytscorebot.entity.User.class);
        when(userRepository.findByChannelId("ch1")).thenReturn(Optional.of(dbUser));

        ChatInputInteractionEvent event = buildStreakEvent(DISCORD_USER_ID, "Wordle", 5);
        listener.handleStreak(event).subscribe();

        verify(streakService).setStreak(eq(dbUser), eq(GameType.WORDLE), eq(5));
        verify(resultsChannelService).refresh();
    }

    @Test
    void handleStreakReturnsUserNotFoundWhenUnknown() {
        when(userRepository.findByChannelId("ch1")).thenReturn(Optional.empty());
        when(userRepository.findByDiscordUserId(DISCORD_USER_ID)).thenReturn(Optional.empty());

        InteractionApplicationCommandCallbackReplyMono replySpec =
                mock(InteractionApplicationCommandCallbackReplyMono.class);
        ChatInputInteractionEvent event = buildStreakEventWithReplyMock(DISCORD_USER_ID, "Wordle", 3, replySpec);

        InteractionApplicationCommandCallbackReplyMono withEphemeralSpec =
                mock(InteractionApplicationCommandCallbackReplyMono.class);
        InteractionApplicationCommandCallbackReplyMono withContentSpec =
                mock(InteractionApplicationCommandCallbackReplyMono.class);
        doReturn(withEphemeralSpec).when(replySpec).withEphemeral(true);
        doReturn(withContentSpec).when(withEphemeralSpec).withContent((String) any());

        listener.handleStreak(event).subscribe();

        verify(withEphemeralSpec).withContent(BotText.MSG_USER_NOT_FOUND);
        verify(streakService, never()).setStreak(any(), any(), anyInt());
    }

    @Test
    void handleStreakRejectsNegativeValue() {
        InteractionApplicationCommandCallbackReplyMono replySpec =
                mock(InteractionApplicationCommandCallbackReplyMono.class);
        ChatInputInteractionEvent event = buildStreakEventWithReplyMock(DISCORD_USER_ID, "Wordle", -1, replySpec);

        InteractionApplicationCommandCallbackReplyMono withEphemeralSpec =
                mock(InteractionApplicationCommandCallbackReplyMono.class);
        InteractionApplicationCommandCallbackReplyMono withContentSpec =
                mock(InteractionApplicationCommandCallbackReplyMono.class);
        doReturn(withEphemeralSpec).when(replySpec).withEphemeral(true);
        doReturn(withContentSpec).when(withEphemeralSpec).withContent((String) any());

        listener.handleStreak(event).subscribe();

        verify(withEphemeralSpec).withContent(BotText.MSG_INVALID_VALUE);
        verify(streakService, never()).setStreak(any(), any(), anyInt());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a mocked {@link ChatInputInteractionEvent} for the {@code /finished} command
     * with a fully wired reply chain: reply() → withEphemeral(true) → withContent(any()).
     */
    private ChatInputInteractionEvent buildEvent(String userId) {
        return buildEvent(userId, "finished");
    }

    private ChatInputInteractionEvent buildEvent(String userId, String commandName) {
        InteractionApplicationCommandCallbackReplyMono replySpec =
                mock(InteractionApplicationCommandCallbackReplyMono.class);
        return buildEventWithReplyMock(userId, commandName, replySpec);
    }

    private ChatInputInteractionEvent buildEventWithReplyMock(
            String userId, InteractionApplicationCommandCallbackReplyMono replySpec) {
        return buildEventWithReplyMock(userId, "finished", replySpec);
    }

    private ChatInputInteractionEvent buildEventWithReplyMock(
            String userId, String commandName, InteractionApplicationCommandCallbackReplyMono replySpec) {
        User discordUser = mock(User.class);
        doReturn(Snowflake.of(userId)).when(discordUser).getId();

        Interaction interaction = mock(Interaction.class);
        doReturn(discordUser).when(interaction).getUser();

        ChatInputInteractionEvent event = mock(ChatInputInteractionEvent.class);
        doReturn(commandName).when(event).getCommandName();
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

    private ChatInputInteractionEvent buildLookupsEvent(String userId, long count) {
        ChatInputInteractionEvent event = buildEvent(userId, "lookups");

        ApplicationCommandInteractionOptionValue optionValue = mock(ApplicationCommandInteractionOptionValue.class);
        doReturn(count).when(optionValue).asLong();

        ApplicationCommandInteractionOption option = mock(ApplicationCommandInteractionOption.class);
        doReturn(Optional.of(optionValue)).when(option).getValue();

        doReturn(Optional.of(option)).when(event).getOption(BotText.CMD_LOOKUPS_OPTION);

        return event;
    }

    private ChatInputInteractionEvent buildStreakEvent(String userId, String game, long streakValue) {
        ChatInputInteractionEvent event = buildEvent(userId, "streak");
        wireStreakOptions(event, game, streakValue);
        return event;
    }

    private ChatInputInteractionEvent buildStreakEventWithReplyMock(
            String userId, String game, long streakValue,
            InteractionApplicationCommandCallbackReplyMono replySpec) {
        ChatInputInteractionEvent event = buildEventWithReplyMock(userId, "streak", replySpec);
        wireStreakOptions(event, game, streakValue);
        return event;
    }

    private void wireStreakOptions(ChatInputInteractionEvent event, String game, long streakValue) {
        ApplicationCommandInteractionOptionValue gameValue = mock(ApplicationCommandInteractionOptionValue.class);
        doReturn(game).when(gameValue).asString();
        ApplicationCommandInteractionOption gameOption = mock(ApplicationCommandInteractionOption.class);
        doReturn(Optional.of(gameValue)).when(gameOption).getValue();
        doReturn(Optional.of(gameOption)).when(event).getOption(BotText.CMD_STREAK_GAME_OPTION);

        ApplicationCommandInteractionOptionValue streakVal = mock(ApplicationCommandInteractionOptionValue.class);
        doReturn(streakValue).when(streakVal).asLong();
        ApplicationCommandInteractionOption streakOption = mock(ApplicationCommandInteractionOption.class);
        doReturn(Optional.of(streakVal)).when(streakOption).getValue();
        doReturn(Optional.of(streakOption)).when(event).getOption(BotText.CMD_STREAK_VALUE_OPTION);
    }
}

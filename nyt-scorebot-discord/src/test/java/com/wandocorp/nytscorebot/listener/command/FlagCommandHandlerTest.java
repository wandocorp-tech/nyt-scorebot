package com.wandocorp.nytscorebot.listener.command;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties.ChannelConfig;
import com.wandocorp.nytscorebot.discord.ResultsChannelService;
import com.wandocorp.nytscorebot.discord.StatusChannelService;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import com.wandocorp.nytscorebot.service.SetFlagOutcome;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.Interaction;
import discord4j.core.object.entity.User;
import discord4j.core.spec.InteractionApplicationCommandCallbackReplyMono;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlagCommandHandlerTest {

    private static final String DISCORD_USER_ID = "123456789";
    private static final String PLAYER_NAME = "TestPlayer";
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 23);

    private ScoreboardService scoreboardService;
    private PuzzleCalendar puzzleCalendar;
    private StatusChannelService statusChannelService;
    private ResultsChannelService resultsChannelService;

    private DuoCommandHandler duoHandler;
    private CheckCommandHandler checkHandler;
    private LookupsCommandHandler lookupsHandler;

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

        duoHandler = new DuoCommandHandler(scoreboardService, puzzleCalendar, channelProperties,
                statusChannelService, resultsChannelService);
        checkHandler = new CheckCommandHandler(scoreboardService, puzzleCalendar, channelProperties,
                statusChannelService, resultsChannelService);
        lookupsHandler = new LookupsCommandHandler(scoreboardService, puzzleCalendar, channelProperties,
                statusChannelService, resultsChannelService);
    }

    // ── FlagReplyHelper ──────────────────────────────────────────────────────

    @Test
    void flagReplyForFlagSetReturnsSetMessage() {
        assertThat(FlagReplyHelper.flagReplyFor(BotText.MSG_DUO_SET, BotText.MSG_DUO_CLEARED, SetFlagOutcome.FLAG_SET))
                .isEqualTo(BotText.MSG_DUO_SET);
    }

    @Test
    void flagReplyForFlagClearedReturnsClearedMessage() {
        assertThat(FlagReplyHelper.flagReplyFor(BotText.MSG_CHECK_SET, BotText.MSG_CHECK_CLEARED, SetFlagOutcome.FLAG_CLEARED))
                .isEqualTo(BotText.MSG_CHECK_CLEARED);
    }

    @Test
    void flagReplyForNoMainCrossword() {
        assertThat(FlagReplyHelper.flagReplyFor(BotText.MSG_DUO_SET, BotText.MSG_DUO_CLEARED, SetFlagOutcome.NO_MAIN_CROSSWORD))
                .isEqualTo(BotText.MSG_NO_MAIN_CROSSWORD);
    }

    @Test
    void flagReplyForNoScoreboard() {
        assertThat(FlagReplyHelper.flagReplyFor(BotText.MSG_DUO_SET, BotText.MSG_DUO_CLEARED, SetFlagOutcome.NO_SCOREBOARD_FOR_DATE))
                .isEqualTo(BotText.MSG_NO_SCOREBOARD_TODAY);
    }

    @Test
    void flagReplyForUserNotFound() {
        assertThat(FlagReplyHelper.flagReplyFor(BotText.MSG_DUO_SET, BotText.MSG_DUO_CLEARED, SetFlagOutcome.USER_NOT_FOUND))
                .isEqualTo(BotText.MSG_USER_NOT_FOUND);
    }

    @Test
    void flagReplyForInvalidValue() {
        assertThat(FlagReplyHelper.flagReplyFor(BotText.MSG_DUO_SET, BotText.MSG_DUO_CLEARED, SetFlagOutcome.INVALID_VALUE))
                .isEqualTo(BotText.MSG_INVALID_VALUE);
    }

    // ── DuoCommandHandler ────────────────────────────────────────────────────

    @Test
    void duoCommandNameIsCorrect() {
        assertThat(duoHandler.commandName()).isEqualTo(BotText.CMD_DUO);
    }

    @Test
    void handleDuoCallsToggleDuo() {
        when(scoreboardService.toggleDuo(DISCORD_USER_ID, TODAY)).thenReturn(SetFlagOutcome.FLAG_SET);
        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID, "duo");
        duoHandler.handle(event).subscribe();
        verify(scoreboardService).toggleDuo(eq(DISCORD_USER_ID), eq(TODAY));
    }

    @Test
    void handleDuoRefreshesMainCrosswordOnSuccess() {
        when(scoreboardService.toggleDuo(any(), any())).thenReturn(SetFlagOutcome.FLAG_SET);
        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID, "duo");
        duoHandler.handle(event).subscribe();
        verify(resultsChannelService).refreshGame(BotText.GAME_LABEL_MAIN);
        verify(statusChannelService).refresh(anyString());
    }

    @Test
    void handleDuoDoesNotRefreshOnNoMainCrossword() {
        when(scoreboardService.toggleDuo(any(), any())).thenReturn(SetFlagOutcome.NO_MAIN_CROSSWORD);
        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID, "duo");
        duoHandler.handle(event).subscribe();
        verify(resultsChannelService, never()).refreshGame(any());
    }

    // ── CheckCommandHandler ──────────────────────────────────────────────────

    @Test
    void checkCommandNameIsCorrect() {
        assertThat(checkHandler.commandName()).isEqualTo(BotText.CMD_CHECK);
    }

    @Test
    void handleCheckCallsToggleCheck() {
        when(scoreboardService.toggleCheck(DISCORD_USER_ID, TODAY)).thenReturn(SetFlagOutcome.FLAG_SET);
        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID, "check");
        checkHandler.handle(event).subscribe();
        verify(scoreboardService).toggleCheck(eq(DISCORD_USER_ID), eq(TODAY));
    }

    @Test
    void handleCheckRefreshesMainCrosswordOnSuccess() {
        when(scoreboardService.toggleCheck(any(), any())).thenReturn(SetFlagOutcome.FLAG_CLEARED);
        ChatInputInteractionEvent event = buildEvent(DISCORD_USER_ID, "check");
        checkHandler.handle(event).subscribe();
        verify(resultsChannelService).refreshGame(BotText.GAME_LABEL_MAIN);
    }

    // ── LookupsCommandHandler ────────────────────────────────────────────────

    @Test
    void lookupsCommandNameIsCorrect() {
        assertThat(lookupsHandler.commandName()).isEqualTo(BotText.CMD_LOOKUPS);
    }

    @Test
    void handleLookupsCallsSetLookups() {
        when(scoreboardService.setLookups(DISCORD_USER_ID, TODAY, 3)).thenReturn(SetFlagOutcome.FLAG_SET);
        ChatInputInteractionEvent event = buildLookupsEvent(DISCORD_USER_ID, 3);
        lookupsHandler.handle(event).subscribe();
        verify(scoreboardService).setLookups(eq(DISCORD_USER_ID), eq(TODAY), eq(3));
    }

    @Test
    void handleLookupsRefreshesMainCrosswordOnSuccess() {
        when(scoreboardService.setLookups(any(), any(), eq(2))).thenReturn(SetFlagOutcome.FLAG_SET);
        ChatInputInteractionEvent event = buildLookupsEvent(DISCORD_USER_ID, 2);
        lookupsHandler.handle(event).subscribe();
        verify(resultsChannelService).refreshGame(BotText.GAME_LABEL_MAIN);
    }

    @Test
    void handleLookupsDoesNotRefreshOnInvalidValue() {
        when(scoreboardService.setLookups(any(), any(), eq(-1))).thenReturn(SetFlagOutcome.INVALID_VALUE);
        ChatInputInteractionEvent event = buildLookupsEvent(DISCORD_USER_ID, -1);
        lookupsHandler.handle(event).subscribe();
        verify(resultsChannelService, never()).refreshGame(any());
    }

    @Test
    void handleLookupsRefreshesOnFlagCleared() {
        when(scoreboardService.setLookups(any(), any(), eq(0))).thenReturn(SetFlagOutcome.FLAG_CLEARED);
        ChatInputInteractionEvent event = buildLookupsEvent(DISCORD_USER_ID, 0);
        lookupsHandler.handle(event).subscribe();
        verify(resultsChannelService).refreshGame(BotText.GAME_LABEL_MAIN);
        verify(statusChannelService).refresh(anyString());
    }

    @Test
    void handleLookupsDoesNotRefreshOnNoScoreboard() {
        when(scoreboardService.setLookups(any(), any(), eq(1))).thenReturn(SetFlagOutcome.NO_SCOREBOARD_FOR_DATE);
        ChatInputInteractionEvent event = buildLookupsEvent(DISCORD_USER_ID, 1);
        lookupsHandler.handle(event).subscribe();
        verify(resultsChannelService, never()).refreshGame(any());
    }

    @Test
    void handleLookupsDoesNotRefreshOnUserNotFound() {
        when(scoreboardService.setLookups(any(), any(), eq(1))).thenReturn(SetFlagOutcome.USER_NOT_FOUND);
        ChatInputInteractionEvent event = buildLookupsEvent(DISCORD_USER_ID, 1);
        lookupsHandler.handle(event).subscribe();
        verify(resultsChannelService, never()).refreshGame(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ChatInputInteractionEvent buildEvent(String userId, String commandName) {
        InteractionApplicationCommandCallbackReplyMono replySpec =
                mock(InteractionApplicationCommandCallbackReplyMono.class);

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
}

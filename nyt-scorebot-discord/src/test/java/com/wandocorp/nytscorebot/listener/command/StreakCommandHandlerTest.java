package com.wandocorp.nytscorebot.listener.command;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties.ChannelConfig;
import com.wandocorp.nytscorebot.discord.ResultsChannelService;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.repository.UserRepository;
import com.wandocorp.nytscorebot.service.StreakService;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.Interaction;
import discord4j.core.object.entity.User;
import discord4j.core.spec.InteractionApplicationCommandCallbackReplyMono;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StreakCommandHandlerTest {

    private static final String DISCORD_USER_ID = "123456789";
    private static final String PLAYER_NAME = "TestPlayer";

    private StreakService streakService;
    private UserRepository userRepository;
    private ResultsChannelService resultsChannelService;
    private StreakCommandHandler handler;

    @BeforeEach
    void setUp() {
        streakService = mock(StreakService.class);
        userRepository = mock(UserRepository.class);
        resultsChannelService = mock(ResultsChannelService.class);

        DiscordChannelProperties channelProperties = new DiscordChannelProperties();
        ChannelConfig ch = new ChannelConfig();
        ch.setId("ch1");
        ch.setName(PLAYER_NAME);
        ch.setUserId(DISCORD_USER_ID);
        channelProperties.setChannels(List.of(ch));

        handler = new StreakCommandHandler(streakService, userRepository, channelProperties, resultsChannelService);
    }

    @Test
    void commandNameIsStreak() {
        assertThat(handler.commandName()).isEqualTo(BotText.CMD_STREAK);
    }

    @Test
    void handleStreakSetsStreakSuccessfully() {
        com.wandocorp.nytscorebot.entity.User dbUser = mock(com.wandocorp.nytscorebot.entity.User.class);
        when(userRepository.findByChannelId("ch1")).thenReturn(Optional.of(dbUser));

        ChatInputInteractionEvent event = buildStreakEvent(DISCORD_USER_ID, "Wordle", 5);
        handler.handle(event).subscribe();

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

        handler.handle(event).subscribe();

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

        handler.handle(event).subscribe();

        verify(withEphemeralSpec).withContent(BotText.MSG_INVALID_VALUE);
        verify(streakService, never()).setStreak(any(), any(), anyInt());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private ChatInputInteractionEvent buildStreakEvent(String userId, String game, long streakValue) {
        ChatInputInteractionEvent event = buildEvent(userId);
        wireStreakOptions(event, game, streakValue);
        return event;
    }

    private ChatInputInteractionEvent buildStreakEventWithReplyMock(
            String userId, String game, long streakValue,
            InteractionApplicationCommandCallbackReplyMono replySpec) {
        ChatInputInteractionEvent event = buildEventWithReplyMock(userId, replySpec);
        wireStreakOptions(event, game, streakValue);
        return event;
    }

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
        doReturn("streak").when(event).getCommandName();
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

package com.wandocorp.nytscorebot.listener;

import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties.ChannelConfig;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;

class StatusMessageListenerTest {

    private static final String STATUS_CHANNEL_ID = "555";
    private static final String BOT_ID = "123";
    private static final Snowflake STATUS_CHANNEL_SNOWFLAKE = Snowflake.of(STATUS_CHANNEL_ID);
    private static final Snowflake BOT_SNOWFLAKE = Snowflake.of(BOT_ID);

    private DiscordChannelProperties channelProperties;
    private StatusMessageListener listener;

    @BeforeEach
    void setUp() {
        channelProperties = new DiscordChannelProperties();
        ChannelConfig ch = new ChannelConfig();
        ch.setId("111");
        ch.setName("Alice");
        ch.setUserId("aaa");
        channelProperties.setChannels(List.of(ch));
        channelProperties.setStatusChannelId(STATUS_CHANNEL_ID);

        listener = new StatusMessageListener(null, channelProperties);
    }

    @Test
    void subscribeDoesNothingWhenStatusChannelIdIsNull() {
        channelProperties.setStatusChannelId(null);
        GatewayDiscordClient client = mock(GatewayDiscordClient.class);
        StatusMessageListener sml = new StatusMessageListener(client, channelProperties);
        sml.subscribe();
        verify(client, never()).on(any(Class.class));
    }

    @Test
    void subscribeDoesNothingWhenStatusChannelIdIsBlank() {
        channelProperties.setStatusChannelId("  ");
        GatewayDiscordClient client = mock(GatewayDiscordClient.class);
        StatusMessageListener sml = new StatusMessageListener(client, channelProperties);
        sml.subscribe();
        verify(client, never()).on(any(Class.class));
    }

    @Test
    void nonBotMessageInStatusChannelIsDeleted() {
        String nonBotUserId = "456";
        MessageCreateEvent event = buildEvent(STATUS_CHANNEL_ID, nonBotUserId);

        listener.listenToEvents(Flux.just(event), STATUS_CHANNEL_SNOWFLAKE, BOT_SNOWFLAKE);

        verify(event.getMessage()).delete();
    }

    @Test
    void botMessageInStatusChannelIsNotDeleted() {
        MessageCreateEvent event = buildEvent(STATUS_CHANNEL_ID, BOT_ID);

        listener.listenToEvents(Flux.just(event), STATUS_CHANNEL_SNOWFLAKE, BOT_SNOWFLAKE);

        verify(event.getMessage(), never()).delete();
    }

    @Test
    void messageInOtherChannelIsNotDeleted() {
        MessageCreateEvent event = buildEvent("999", "456");

        listener.listenToEvents(Flux.just(event), STATUS_CHANNEL_SNOWFLAKE, BOT_SNOWFLAKE);

        verify(event.getMessage(), never()).delete();
    }

    private MessageCreateEvent buildEvent(String channelId, String authorId) {
        User discordUser = mock(User.class);
        doReturn(Snowflake.of(authorId)).when(discordUser).getId();

        Message message = mock(Message.class);
        doReturn(Snowflake.of(channelId)).when(message).getChannelId();
        doReturn(Optional.of(discordUser)).when(message).getAuthor();
        doReturn(Mono.empty()).when(message).delete();

        MessageCreateEvent event = mock(MessageCreateEvent.class);
        doReturn(message).when(event).getMessage();
        return event;
    }
}

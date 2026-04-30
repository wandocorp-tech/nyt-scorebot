package com.wandocorp.nytscorebot.discord;

import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.MessageCreateMono;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageSlotWriterTest {

    private static final Snowflake CHANNEL = Snowflake.of("123");
    private static final Snowflake EXISTING = Snowflake.of("999");
    private static final Snowflake NEW_ID = Snowflake.of("1000");
    private static final String CONTENT = "hello";

    private GatewayDiscordClient client;
    private MessageChannel channel;
    private MessageSlotWriter writer;

    @BeforeEach
    void setUp() {
        client = mock(GatewayDiscordClient.class);
        channel = mock(MessageChannel.class);
        writer = new MessageSlotWriter(client);
    }

    private MessageCreateMono stubCreateMessage(Message msg) {
        MessageCreateMono mcMono = mock(MessageCreateMono.class);
        doAnswer(inv -> {
            CoreSubscriber<? super Message> sub = inv.getArgument(0);
            Mono.just(msg).subscribe(sub);
            return null;
        }).when(mcMono).subscribe(any(CoreSubscriber.class));
        when(channel.createMessage(CONTENT)).thenReturn(mcMono);
        return mcMono;
    }

    @Test
    void postsFreshWhenNoExistingId() {
        Message posted = mock(Message.class);
        when(posted.getId()).thenReturn(NEW_ID);
        stubCreateMessage(posted);
        when(client.getChannelById(CHANNEL)).thenReturn(Mono.just(channel));

        assertThat(writer.editOrPost(CHANNEL, null, CONTENT).block()).isEqualTo(NEW_ID);

        verify(client, never()).getMessageById(any(Snowflake.class), any(Snowflake.class));
        verify(channel).createMessage(CONTENT);
    }

    @Test
    void editsInPlaceWhenExistingIdAndEditSucceeds() {
        Message existing = mock(Message.class);
        Message edited = mock(Message.class);
        when(edited.getId()).thenReturn(EXISTING);
        when(existing.edit(any(java.util.function.Consumer.class))).thenReturn(Mono.just(edited));
        when(client.getMessageById(CHANNEL, EXISTING)).thenReturn(Mono.just(existing));

        assertThat(writer.editOrPost(CHANNEL, EXISTING, CONTENT).block()).isEqualTo(EXISTING);

        verify(client).getMessageById(CHANNEL, EXISTING);
        verify(existing).edit(any(java.util.function.Consumer.class));
        verify(client, never()).getChannelById(any(Snowflake.class));
    }

    @Test
    void fallsBackToPostWhenEditErrors() {
        Message existing = mock(Message.class);
        when(existing.edit(any(java.util.function.Consumer.class)))
                .thenReturn(Mono.error(new RuntimeException("404 Not Found")));
        when(client.getMessageById(CHANNEL, EXISTING)).thenReturn(Mono.just(existing));

        Message posted = mock(Message.class);
        when(posted.getId()).thenReturn(NEW_ID);
        stubCreateMessage(posted);
        when(client.getChannelById(CHANNEL)).thenReturn(Mono.just(channel));

        assertThat(writer.editOrPost(CHANNEL, EXISTING, CONTENT).block()).isEqualTo(NEW_ID);

        verify(existing).edit(any(java.util.function.Consumer.class));
        verify(channel).createMessage(CONTENT);
    }

    @Test
    void fallsBackToPostWhenGetMessageByIdErrors() {
        when(client.getMessageById(CHANNEL, EXISTING))
                .thenReturn(Mono.error(new RuntimeException("Unknown Message")));

        Message posted = mock(Message.class);
        when(posted.getId()).thenReturn(NEW_ID);
        stubCreateMessage(posted);
        when(client.getChannelById(CHANNEL)).thenReturn(Mono.just(channel));

        assertThat(writer.editOrPost(CHANNEL, EXISTING, CONTENT).block()).isEqualTo(NEW_ID);

        verify(channel).createMessage(CONTENT);
    }

    @Test
    void fallsBackToPostWhenGetMessageByIdReturnsEmpty() {
        when(client.getMessageById(CHANNEL, EXISTING)).thenReturn(Mono.empty());

        Message posted = mock(Message.class);
        when(posted.getId()).thenReturn(NEW_ID);
        stubCreateMessage(posted);
        when(client.getChannelById(eq(CHANNEL))).thenReturn(Mono.just(channel));

        assertThat(writer.editOrPost(CHANNEL, EXISTING, CONTENT).block()).isEqualTo(NEW_ID);

        verify(channel).createMessage(CONTENT);
    }
}

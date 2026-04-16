package com.wandocorp.nytscorebot.listener;

import com.wandocorp.nytscorebot.listener.command.SlashCommandHandler;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.Mockito.*;

class SlashCommandListenerTest {

    private SlashCommandHandler finishedHandler;
    private SlashCommandHandler duoHandler;
    private SlashCommandListener listener;

    @BeforeEach
    void setUp() {
        finishedHandler = mock(SlashCommandHandler.class);
        when(finishedHandler.commandName()).thenReturn("finished");
        when(finishedHandler.handle(any())).thenReturn(Mono.empty());

        duoHandler = mock(SlashCommandHandler.class);
        when(duoHandler.commandName()).thenReturn("duo");
        when(duoHandler.handle(any())).thenReturn(Mono.empty());

        listener = new SlashCommandListener(null, List.of(finishedHandler, duoHandler));
    }

    @Test
    void finishedCommandIsDispatchedToHandler() {
        ChatInputInteractionEvent event = mock(ChatInputInteractionEvent.class);
        doReturn("finished").when(event).getCommandName();

        listener.listenToEvents(Flux.just(event));

        verify(finishedHandler).handle(event);
    }

    @Test
    void duoCommandIsDispatchedToHandler() {
        ChatInputInteractionEvent event = mock(ChatInputInteractionEvent.class);
        doReturn("duo").when(event).getCommandName();

        listener.listenToEvents(Flux.just(event));

        verify(duoHandler).handle(event);
    }

    @Test
    void unknownCommandIsIgnored() {
        ChatInputInteractionEvent event = mock(ChatInputInteractionEvent.class);
        doReturn("leaderboard").when(event).getCommandName();

        listener.listenToEvents(Flux.just(event));

        verify(finishedHandler, never()).handle(any());
        verify(duoHandler, never()).handle(any());
    }

    @Test
    void multipleEventsAreDispatched() {
        ChatInputInteractionEvent finishedEvent = mock(ChatInputInteractionEvent.class);
        doReturn("finished").when(finishedEvent).getCommandName();

        ChatInputInteractionEvent duoEvent = mock(ChatInputInteractionEvent.class);
        doReturn("duo").when(duoEvent).getCommandName();

        listener.listenToEvents(Flux.just(finishedEvent, duoEvent));

        verify(finishedHandler).handle(finishedEvent);
        verify(duoHandler).handle(duoEvent);
    }
}

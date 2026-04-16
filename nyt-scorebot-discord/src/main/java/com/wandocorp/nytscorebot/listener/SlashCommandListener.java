package com.wandocorp.nytscorebot.listener;

import com.wandocorp.nytscorebot.listener.command.SlashCommandHandler;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SlashCommandListener {

    private final GatewayDiscordClient client;
    private final Map<String, SlashCommandHandler> handlers;

    public SlashCommandListener(GatewayDiscordClient client, List<SlashCommandHandler> handlers) {
        this.client = client;
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(SlashCommandHandler::commandName, Function.identity()));
    }

    @PostConstruct
    public void subscribe() {
        listenToEvents(client.on(ChatInputInteractionEvent.class));
    }

    void listenToEvents(Flux<ChatInputInteractionEvent> events) {
        events
                .flatMap(event -> {
                    SlashCommandHandler handler = handlers.get(event.getCommandName());
                    return handler != null ? handler.handle(event) : Mono.empty();
                })
                .subscribe(
                        v -> {},
                        error -> log.error("Error in slash command listener pipeline", error));
    }
}

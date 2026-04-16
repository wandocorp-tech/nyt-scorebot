package com.wandocorp.nytscorebot.listener.command;

import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import reactor.core.publisher.Mono;

/**
 * Strategy interface for handling a single Discord slash command.
 * Implementations are discovered via Spring component scanning and
 * registered automatically in {@link com.wandocorp.nytscorebot.listener.SlashCommandListener}.
 */
public interface SlashCommandHandler {

    /** The slash command name this handler responds to (e.g. "finished"). */
    String commandName();

    /** Handle the interaction event and return the reply chain. */
    Mono<Void> handle(ChatInputInteractionEvent event);
}

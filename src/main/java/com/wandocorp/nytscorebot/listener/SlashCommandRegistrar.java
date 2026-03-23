package com.wandocorp.nytscorebot.listener;

import discord4j.core.GatewayDiscordClient;
import discord4j.discordjson.json.ApplicationCommandRequest;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SlashCommandRegistrar {

    private static final Logger log = LoggerFactory.getLogger(SlashCommandRegistrar.class);

    private final GatewayDiscordClient client;

    public SlashCommandRegistrar(GatewayDiscordClient client) {
        this.client = client;
    }

    @PostConstruct
    public void registerCommands() {
        long applicationId = client.getRestClient().getApplicationId().block();

        ApplicationCommandRequest finishedCommand = ApplicationCommandRequest.builder()
                .name("finished")
                .description("Mark your scorecard as complete for today")
                .build();

        client.getRestClient().getApplicationService()
                .createGlobalApplicationCommand(applicationId, finishedCommand)
                .doOnNext(cmd -> log.info("Registered global slash command: /{}", cmd.name()))
                .doOnError(e -> log.error("Failed to register /finished command", e))
                .subscribe();
    }
}

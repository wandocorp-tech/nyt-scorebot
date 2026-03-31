package com.wandocorp.nytscorebot.listener;

import com.wandocorp.nytscorebot.BotText;
import discord4j.core.GatewayDiscordClient;
import discord4j.discordjson.json.ApplicationCommandRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class SlashCommandRegistrar {

    private final GatewayDiscordClient client;

    @PostConstruct
    public void registerCommands() {
        long applicationId = client.getRestClient().getApplicationId().block();

        ApplicationCommandRequest finishedCommand = ApplicationCommandRequest.builder()
                .name(BotText.CMD_FINISHED)
                .description(BotText.CMD_FINISHED_DESCRIPTION)
                .build();

        client.getRestClient().getApplicationService()
                .createGlobalApplicationCommand(applicationId, finishedCommand)
                .doOnNext(cmd -> log.info("Registered global slash command: /{}", cmd.name()))
                .doOnError(e -> log.error("Failed to register /finished command", e))
                .subscribe();
    }
}

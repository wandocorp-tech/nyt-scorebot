package com.wandocorp.nytscorebot.listener;

import com.wandocorp.nytscorebot.BotText;
import discord4j.core.GatewayDiscordClient;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
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

        ApplicationCommandRequest duoCommand = ApplicationCommandRequest.builder()
                .name(BotText.CMD_DUO)
                .description(BotText.CMD_DUO_DESCRIPTION)
                .build();

        ApplicationCommandRequest lookupsCommand = ApplicationCommandRequest.builder()
                .name(BotText.CMD_LOOKUPS)
                .description(BotText.CMD_LOOKUPS_DESCRIPTION)
                .addOption(ApplicationCommandOptionData.builder()
                        .name(BotText.CMD_LOOKUPS_OPTION)
                        .description(BotText.CMD_LOOKUPS_OPTION_DESC)
                        .type(4) // INTEGER
                        .required(true)
                        .build())
                .build();

        ApplicationCommandRequest checkCommand = ApplicationCommandRequest.builder()
                .name(BotText.CMD_CHECK)
                .description(BotText.CMD_CHECK_DESCRIPTION)
                .build();

        ApplicationCommandRequest streakCommand = ApplicationCommandRequest.builder()
                .name(BotText.CMD_STREAK)
                .description(BotText.CMD_STREAK_DESCRIPTION)
                .addOption(ApplicationCommandOptionData.builder()
                        .name(BotText.CMD_STREAK_GAME_OPTION)
                        .description(BotText.CMD_STREAK_GAME_OPTION_DESC)
                        .type(3) // STRING
                        .required(true)
                        .addChoice(ApplicationCommandOptionChoiceData.builder()
                                .name(BotText.GAME_LABEL_WORDLE).value(BotText.GAME_LABEL_WORDLE).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder()
                                .name(BotText.GAME_LABEL_CONNECTIONS).value(BotText.GAME_LABEL_CONNECTIONS).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder()
                                .name(BotText.GAME_LABEL_STRANDS).value(BotText.GAME_LABEL_STRANDS).build())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name(BotText.CMD_STREAK_VALUE_OPTION)
                        .description(BotText.CMD_STREAK_VALUE_OPTION_DESC)
                        .type(4) // INTEGER
                        .required(true)
                        .build())
                .build();

        var appService = client.getRestClient().getApplicationService();
        for (ApplicationCommandRequest cmd : new ApplicationCommandRequest[]{
                finishedCommand, duoCommand, lookupsCommand, checkCommand, streakCommand}) {
            appService.createGlobalApplicationCommand(applicationId, cmd)
                    .doOnNext(c -> log.info("Registered global slash command: /{}", c.name()))
                    .doOnError(e -> log.error("Failed to register command", e))
                    .subscribe();
        }
    }
}

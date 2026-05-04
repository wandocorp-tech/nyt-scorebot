package com.wandocorp.nytscorebot.listener;

import com.wandocorp.nytscorebot.BotText;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@RequiredArgsConstructor
@Component
public class SlashCommandRegistrar {

    private final GatewayDiscordClient client;

    @PostConstruct
    public void registerCommands() {
        var appService = client.getRestClient().getApplicationService();
        ApplicationCommandRequest[] commands = buildAllCommands();

        client.getRestClient().getApplicationId()
                .flatMapMany(applicationId -> Flux.fromArray(commands)
                        .flatMap(cmd -> appService.createGlobalApplicationCommand(applicationId, cmd)))
                .doOnNext(c -> log.info("Registered global slash command: /{}", c.name()))
                .doOnError(e -> log.error("Failed to register slash commands", e))
                .subscribe(
                        v -> {},
                        error -> log.error("Error registering slash commands", error));
    }

    private static ApplicationCommandRequest[] buildAllCommands() {
        return new ApplicationCommandRequest[]{
                buildFinishedCommand(),
                buildDuoCommand(),
                buildLookupsCommand(),
                buildCheckCommand(),
                buildStreakCommand(),
                buildStatsCommand()
        };
    }

    private static ApplicationCommandRequest buildFinishedCommand() {
        return ApplicationCommandRequest.builder()
                .name(BotText.CMD_FINISHED)
                .description(BotText.CMD_FINISHED_DESCRIPTION)
                .build();
    }

    private static ApplicationCommandRequest buildDuoCommand() {
        return ApplicationCommandRequest.builder()
                .name(BotText.CMD_DUO)
                .description(BotText.CMD_DUO_DESCRIPTION)
                .build();
    }

    private static ApplicationCommandRequest buildLookupsCommand() {
        return ApplicationCommandRequest.builder()
                .name(BotText.CMD_LOOKUPS)
                .description(BotText.CMD_LOOKUPS_DESCRIPTION)
                .addOption(ApplicationCommandOptionData.builder()
                        .name(BotText.CMD_LOOKUPS_OPTION)
                        .description(BotText.CMD_LOOKUPS_OPTION_DESC)
                        .type(ApplicationCommandOption.Type.INTEGER.getValue())
                        .required(true)
                        .build())
                .build();
    }

    private static ApplicationCommandRequest buildCheckCommand() {
        return ApplicationCommandRequest.builder()
                .name(BotText.CMD_CHECK)
                .description(BotText.CMD_CHECK_DESCRIPTION)
                .build();
    }

    private static ApplicationCommandRequest buildStreakCommand() {
        return ApplicationCommandRequest.builder()
                .name(BotText.CMD_STREAK)
                .description(BotText.CMD_STREAK_DESCRIPTION)
                .addOption(ApplicationCommandOptionData.builder()
                        .name(BotText.CMD_STREAK_GAME_OPTION)
                        .description(BotText.CMD_STREAK_GAME_OPTION_DESC)
                        .type(ApplicationCommandOption.Type.STRING.getValue())
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
                        .type(ApplicationCommandOption.Type.INTEGER.getValue())
                        .required(true)
                        .minValue(0D)
                        .build())
                .build();
    }

    private static ApplicationCommandRequest buildStatsCommand() {
        return ApplicationCommandRequest.builder()
                .name(BotText.CMD_STATS)
                .description(BotText.CMD_STATS_DESCRIPTION)
                .addOption(ApplicationCommandOptionData.builder()
                        .name(BotText.CMD_STATS_GAME_OPTION)
                        .description(BotText.CMD_STATS_GAME_OPTION_DESC)
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .required(true)
                        .addChoice(ApplicationCommandOptionChoiceData.builder()
                                .name(BotText.GAME_LABEL_MINI).value(BotText.STATS_GAME_MINI).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder()
                                .name(BotText.GAME_LABEL_MIDI).value(BotText.STATS_GAME_MIDI).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder()
                                .name(BotText.GAME_LABEL_MAIN).value(BotText.STATS_GAME_MAIN).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder()
                                .name("All").value(BotText.STATS_GAME_ALL).build())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name(BotText.CMD_STATS_PERIOD_OPTION)
                        .description(BotText.CMD_STATS_PERIOD_OPTION_DESC)
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .required(true)
                        .addChoice(ApplicationCommandOptionChoiceData.builder()
                                .name("Week").value(BotText.STATS_PERIOD_WEEK).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder()
                                .name("Month").value(BotText.STATS_PERIOD_MONTH).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder()
                                .name("Year").value(BotText.STATS_PERIOD_YEAR).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder()
                                .name("All-Time").value(BotText.STATS_PERIOD_ALL_TIME).build())
                        .addChoice(ApplicationCommandOptionChoiceData.builder()
                                .name("Custom").value(BotText.STATS_PERIOD_CUSTOM).build())
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name(BotText.CMD_STATS_FROM_OPTION)
                        .description(BotText.CMD_STATS_FROM_OPTION_DESC)
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .required(false)
                        .build())
                .addOption(ApplicationCommandOptionData.builder()
                        .name(BotText.CMD_STATS_TO_OPTION)
                        .description(BotText.CMD_STATS_TO_OPTION_DESC)
                        .type(ApplicationCommandOption.Type.STRING.getValue())
                        .required(false)
                        .build())
                .build();
    }
}

package com.wandocorp.nytscorebot.listener.command;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.discord.ResultsChannelService;
import com.wandocorp.nytscorebot.discord.StatusChannelService;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import com.wandocorp.nytscorebot.service.SetFlagOutcome;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import static com.wandocorp.nytscorebot.listener.command.FlagReplyHelper.refreshMainCrossword;

@Slf4j
@RequiredArgsConstructor
@Component
public class LookupsCommandHandler implements SlashCommandHandler {

    private final ScoreboardService scoreboardService;
    private final PuzzleCalendar puzzleCalendar;
    private final DiscordChannelProperties channelProperties;
    private final StatusChannelService statusChannelService;
    private final ResultsChannelService resultsChannelService;

    @Override
    public String commandName() {
        return BotText.CMD_LOOKUPS;
    }

    @Override
    public Mono<Void> handle(ChatInputInteractionEvent event) {
        String discordUserId = event.getInteraction().getUser().getId().asString();
        log.info("Received /lookups from Discord user {}", discordUserId);

        long count = event.getOption(BotText.CMD_LOOKUPS_OPTION)
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asLong)
                .orElse(0L);

        int countInt;
        try {
            countInt = Math.toIntExact(count);
        } catch (ArithmeticException e) {
            return event.reply().withEphemeral(true).withContent(BotText.MSG_INVALID_VALUE);
        }

        SetFlagOutcome outcome = scoreboardService.setLookups(discordUserId, puzzleCalendar.today(), countInt);
        if (outcome == SetFlagOutcome.FLAG_SET || outcome == SetFlagOutcome.FLAG_CLEARED) {
            refreshMainCrossword(discordUserId, channelProperties, statusChannelService, resultsChannelService);
        }
        String reply = switch (outcome) {
            case FLAG_SET              -> String.format(BotText.MSG_LOOKUPS_SET, countInt);
            case FLAG_CLEARED          -> BotText.MSG_LOOKUPS_CLEARED;
            case NO_MAIN_CROSSWORD     -> BotText.MSG_NO_MAIN_CROSSWORD;
            case NO_SCOREBOARD_FOR_DATE -> BotText.MSG_NO_SCOREBOARD_TODAY;
            case USER_NOT_FOUND        -> BotText.MSG_USER_NOT_FOUND;
            case INVALID_VALUE         -> BotText.MSG_INVALID_VALUE;
        };
        return event.reply().withEphemeral(true).withContent(reply);
    }
}

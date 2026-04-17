package com.wandocorp.nytscorebot.listener.command;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.discord.ResultsChannelService;
import com.wandocorp.nytscorebot.discord.StatusChannelService;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import com.wandocorp.nytscorebot.service.SetFlagOutcome;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import static com.wandocorp.nytscorebot.listener.command.FlagReplyHelper.flagReplyFor;
import static com.wandocorp.nytscorebot.listener.command.FlagReplyHelper.refreshMainCrossword;

@Slf4j
@RequiredArgsConstructor
@Component
public class DuoCommandHandler implements SlashCommandHandler {

    private final ScoreboardService scoreboardService;
    private final PuzzleCalendar puzzleCalendar;
    private final DiscordChannelProperties channelProperties;
    private final StatusChannelService statusChannelService;
    private final ResultsChannelService resultsChannelService;

    @Override
    public String commandName() {
        return BotText.CMD_DUO;
    }

    @Override
    public Mono<Void> handle(ChatInputInteractionEvent event) {
        String discordUserId = event.getInteraction().getUser().getId().asString();
        log.info("Received /duo from Discord user {}", discordUserId);

        SetFlagOutcome outcome = scoreboardService.toggleDuo(discordUserId, puzzleCalendar.today());
        if (outcome == SetFlagOutcome.FLAG_SET || outcome == SetFlagOutcome.FLAG_CLEARED) {
            refreshMainCrossword(discordUserId, channelProperties, statusChannelService, resultsChannelService);
        }
        String reply = flagReplyFor(BotText.MSG_DUO_SET, BotText.MSG_DUO_CLEARED, outcome);
        return event.reply().withEphemeral(true).withContent(reply);
    }
}

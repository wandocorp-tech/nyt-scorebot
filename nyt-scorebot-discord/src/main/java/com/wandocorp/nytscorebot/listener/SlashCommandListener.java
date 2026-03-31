package com.wandocorp.nytscorebot.listener;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.service.MarkFinishedOutcome;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.discord.ResultsChannelService;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import com.wandocorp.nytscorebot.discord.StatusChannelService;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@Component
public class SlashCommandListener {

    private final GatewayDiscordClient client;
    private final ScoreboardService scoreboardService;
    private final PuzzleCalendar puzzleCalendar;
    private final StatusChannelService statusChannelService;
    private final ResultsChannelService resultsChannelService;
    private final DiscordChannelProperties channelProperties;

    @PostConstruct
    public void subscribe() {
        listenToEvents(client.on(ChatInputInteractionEvent.class));
    }

    void listenToEvents(Flux<ChatInputInteractionEvent> events) {
        events
                .filter(event -> BotText.CMD_FINISHED.equals(event.getCommandName()))
                .flatMap(this::handleFinished)
                .subscribe();
    }

    Mono<Void> handleFinished(ChatInputInteractionEvent event) {
        String discordUserId = event.getInteraction().getUser().getId().asString();
        log.info("Received /finished from Discord user {}", discordUserId);

        MarkFinishedOutcome outcome = scoreboardService.markFinished(discordUserId, puzzleCalendar.today());
        if (outcome == MarkFinishedOutcome.MARKED_FINISHED || outcome == MarkFinishedOutcome.ALREADY_FINISHED) {
            String playerName = channelProperties.getChannels().stream()
                    .filter(c -> c.getUserId().equals(discordUserId))
                    .map(DiscordChannelProperties.ChannelConfig::getName)
                    .findFirst()
                    .orElse(discordUserId);
            String contextMessage = String.format(BotText.STATUS_CONTEXT_PLAYER_FINISHED, playerName);
            statusChannelService.refresh(contextMessage);
            resultsChannelService.refresh();
        }
        String reply = replyMessageFor(outcome);

        return event.reply()
                .withEphemeral(true)
                .withContent(reply);
    }

    static String replyMessageFor(MarkFinishedOutcome outcome) {
        return switch (outcome) {
            case MARKED_FINISHED       -> BotText.MSG_MARKED_FINISHED;
            case ALREADY_FINISHED      -> BotText.MSG_ALREADY_FINISHED;
            case NO_SCOREBOARD_FOR_DATE -> BotText.MSG_NO_SCOREBOARD_TODAY;
            case USER_NOT_FOUND        -> BotText.MSG_USER_NOT_FOUND;
        };
    }
}

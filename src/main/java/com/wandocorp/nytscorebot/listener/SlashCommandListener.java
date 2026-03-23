package com.wandocorp.nytscorebot.listener;

import com.wandocorp.nytscorebot.service.MarkCompleteOutcome;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class SlashCommandListener {

    private static final Logger log = LoggerFactory.getLogger(SlashCommandListener.class);

    private final GatewayDiscordClient client;
    private final ScoreboardService scoreboardService;
    private final PuzzleCalendar puzzleCalendar;

    public SlashCommandListener(GatewayDiscordClient client,
                                ScoreboardService scoreboardService,
                                PuzzleCalendar puzzleCalendar) {
        this.client = client;
        this.scoreboardService = scoreboardService;
        this.puzzleCalendar = puzzleCalendar;
    }

    @PostConstruct
    public void subscribe() {
        listenToEvents(client.on(ChatInputInteractionEvent.class));
    }

    void listenToEvents(Flux<ChatInputInteractionEvent> events) {
        events
                .filter(event -> "finished".equals(event.getCommandName()))
                .flatMap(this::handleFinished)
                .subscribe();
    }

    Mono<Void> handleFinished(ChatInputInteractionEvent event) {
        String discordUserId = event.getInteraction().getUser().getId().asString();
        log.info("Received /finished from Discord user {}", discordUserId);

        MarkCompleteOutcome outcome = scoreboardService.markComplete(discordUserId, puzzleCalendar.today());
        String reply = replyMessageFor(outcome);

        return event.reply()
                .withEphemeral(true)
                .withContent(reply);
    }

    static String replyMessageFor(MarkCompleteOutcome outcome) {
        return switch (outcome) {
            case MARKED_COMPLETE -> "✅ Your scoreboard for today has been marked as complete!";
            case ALREADY_COMPLETE -> "Your scoreboard was already marked complete for today.";
            case NO_SCOREBOARD_FOR_DATE -> "You haven't submitted any results for today yet.";
            case USER_NOT_FOUND -> "You are not a tracked user in this bot.";
        };
    }
}

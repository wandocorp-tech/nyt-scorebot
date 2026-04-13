package com.wandocorp.nytscorebot.listener;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.repository.UserRepository;
import com.wandocorp.nytscorebot.service.MarkFinishedOutcome;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.service.SetFlagOutcome;
import com.wandocorp.nytscorebot.service.StreakService;
import com.wandocorp.nytscorebot.discord.ResultsChannelService;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import com.wandocorp.nytscorebot.discord.StatusChannelService;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
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
    private final StreakService streakService;
    private final UserRepository userRepository;
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
                .flatMap(event -> switch (event.getCommandName()) {
                    case BotText.CMD_FINISHED -> handleFinished(event);
                    case BotText.CMD_DUO      -> handleDuo(event);
                    case BotText.CMD_LOOKUPS  -> handleLookups(event);
                    case BotText.CMD_CHECK    -> handleCheck(event);
                    case BotText.CMD_STREAK   -> handleStreak(event);
                    default -> Mono.empty();
                })
                .subscribe(
                        v -> {},
                        error -> log.error("Error in slash command listener pipeline", error));
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

    // ── Flag command handlers ─────────────────────────────────────────────────

    Mono<Void> handleDuo(ChatInputInteractionEvent event) {
        String discordUserId = event.getInteraction().getUser().getId().asString();
        log.info("Received /duo from Discord user {}", discordUserId);

        SetFlagOutcome outcome = scoreboardService.toggleDuo(discordUserId, puzzleCalendar.today());
        if (outcome == SetFlagOutcome.FLAG_SET || outcome == SetFlagOutcome.FLAG_CLEARED) {
            refreshMainCrossword(discordUserId);
        }
        String reply = flagReplyFor(BotText.MSG_DUO_SET, BotText.MSG_DUO_CLEARED, outcome);
        return event.reply().withEphemeral(true).withContent(reply);
    }

    Mono<Void> handleLookups(ChatInputInteractionEvent event) {
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
            refreshMainCrossword(discordUserId);
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

    Mono<Void> handleCheck(ChatInputInteractionEvent event) {
        String discordUserId = event.getInteraction().getUser().getId().asString();
        log.info("Received /check from Discord user {}", discordUserId);

        SetFlagOutcome outcome = scoreboardService.toggleCheck(discordUserId, puzzleCalendar.today());
        if (outcome == SetFlagOutcome.FLAG_SET || outcome == SetFlagOutcome.FLAG_CLEARED) {
            refreshMainCrossword(discordUserId);
        }
        String reply = flagReplyFor(BotText.MSG_CHECK_SET, BotText.MSG_CHECK_CLEARED, outcome);
        return event.reply().withEphemeral(true).withContent(reply);
    }

    private void refreshMainCrossword(String discordUserId) {
        String playerName = channelProperties.getChannels().stream()
                .filter(c -> c.getUserId().equals(discordUserId))
                .map(DiscordChannelProperties.ChannelConfig::getName)
                .findFirst()
                .orElse(discordUserId);
        String contextMessage = String.format(BotText.STATUS_CONTEXT_PLAYER_FINISHED, playerName);
        statusChannelService.refresh(contextMessage);
        resultsChannelService.refreshGame(BotText.GAME_LABEL_MAIN);
    }

    Mono<Void> handleStreak(ChatInputInteractionEvent event) {
        String discordUserId = event.getInteraction().getUser().getId().asString();
        log.info("Received /streak from Discord user {}", discordUserId);

        String game = event.getOption(BotText.CMD_STREAK_GAME_OPTION)
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString)
                .orElse("");

        long streakValue = event.getOption(BotText.CMD_STREAK_VALUE_OPTION)
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asLong)
                .orElse(0L);

        int streakInt;
        try {
            streakInt = Math.toIntExact(streakValue);
        } catch (ArithmeticException e) {
            return event.reply().withEphemeral(true)
                    .withContent(BotText.MSG_INVALID_VALUE);
        }
        if (streakInt < 0) {
            return event.reply().withEphemeral(true)
                    .withContent(BotText.MSG_INVALID_VALUE);
        }

        java.util.Optional<User> userOpt = channelProperties.getChannels().stream()
                .filter(c -> discordUserId.equals(c.getUserId()))
                .findFirst()
                .flatMap(c -> userRepository.findByChannelId(c.getId()))
                .or(() -> userRepository.findByUserId(discordUserId));
        if (userOpt.isEmpty()) {
            return event.reply().withEphemeral(true)
                    .withContent(BotText.MSG_USER_NOT_FOUND);
        }

        streakService.setStreak(userOpt.get(), game, streakInt);
        resultsChannelService.refresh();

        String reply = String.format(BotText.MSG_STREAK_SET, game, streakValue);
        return event.reply().withEphemeral(true).withContent(reply);
    }

    static String flagReplyFor(String setMsg, String clearedMsg, SetFlagOutcome outcome) {
        return switch (outcome) {
            case FLAG_SET              -> setMsg;
            case FLAG_CLEARED          -> clearedMsg;
            case NO_MAIN_CROSSWORD     -> BotText.MSG_NO_MAIN_CROSSWORD;
            case NO_SCOREBOARD_FOR_DATE -> BotText.MSG_NO_SCOREBOARD_TODAY;
            case USER_NOT_FOUND        -> BotText.MSG_USER_NOT_FOUND;
            case INVALID_VALUE         -> BotText.MSG_INVALID_VALUE;
        };
    }
}

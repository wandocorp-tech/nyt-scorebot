package com.wandocorp.nytscorebot.listener;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties.ChannelConfig;
import com.wandocorp.nytscorebot.model.GameResult;
import com.wandocorp.nytscorebot.parser.GameResultParser;
import com.wandocorp.nytscorebot.discord.ResultsChannelService;
import com.wandocorp.nytscorebot.service.PbUpdateOutcome;
import com.wandocorp.nytscorebot.service.SaveOutcome;
import com.wandocorp.nytscorebot.service.SaveResult;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import com.wandocorp.nytscorebot.discord.StatusChannelService;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.channel.MessageChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class MessageListener {

    private final GatewayDiscordClient client;
    private final GameResultParser parser;
    private final ScoreboardService scoreboardService;
    private final StatusChannelService statusChannelService;
    private final ResultsChannelService resultsChannelService;
    private final Map<Snowflake, String> channelPersonMap;
    private final Map<Snowflake, String> channelUserIdMap;

    public MessageListener(GatewayDiscordClient client,
                           DiscordChannelProperties channelProperties,
                           GameResultParser parser,
                           ScoreboardService scoreboardService,
                           StatusChannelService statusChannelService,
                           ResultsChannelService resultsChannelService) {
        this.client = client;
        this.parser = parser;
        this.scoreboardService = scoreboardService;
        this.statusChannelService = statusChannelService;
        this.resultsChannelService = resultsChannelService;
        this.channelPersonMap = channelProperties.getChannels().stream()
                .collect(Collectors.toMap(
                        c -> Snowflake.of(c.getId()),
                        ChannelConfig::getName
                ));
        this.channelUserIdMap = channelProperties.getChannels().stream()
                .collect(Collectors.toMap(
                        c -> Snowflake.of(c.getId()),
                        ChannelConfig::getUserId
                ));
        log.info("Monitoring {} channel(s): {}", channelPersonMap.size(),
                channelProperties.getChannels().stream()
                        .map(c -> c.getName() + "=" + c.getId())
                        .collect(Collectors.joining(", ")));
    }

    public boolean isChannelMonitored(Snowflake channelId) {
        return channelPersonMap.containsKey(channelId);
    }

    public boolean isFromConfiguredUser(Snowflake channelId, String authorId) {
        String configuredUserId = channelUserIdMap.get(channelId);
        return configuredUserId != null && configuredUserId.equals(authorId);
    }

    public Mono<Void> processMessage(Snowflake channelId, String content, Mono<MessageChannel> channelMono) {
        String personName = channelPersonMap.get(channelId);
        String discordUserId = channelUserIdMap.get(channelId);
        return parser.parse(content, personName)
                .map(result -> {
                    log.info("Parsed game result for {}: {}", personName, result);
                    SaveResult saveResult = scoreboardService.saveResult(
                            channelId.asString(), personName, discordUserId, result);
                    SaveOutcome outcome = saveResult.outcome();
                    if (outcome == SaveOutcome.SAVED) {
                        String contextMessage = String.format(BotText.STATUS_CONTEXT_GAME_SUBMITTED, personName, gameLabel(result));
                        statusChannelService.refresh(contextMessage);
                        if (resultsChannelService.hasPostedResults()) {
                            resultsChannelService.refreshGame(gameLabel(result));
                        } else {
                            resultsChannelService.refresh();
                        }
                    }
                    Mono<Void> reply = replyForOutcome(channelMono, outcome);
                    if (saveResult.pb() instanceof PbUpdateOutcome.NewPb npb) {
                        Mono<Void> pbAnnounce = channelMono
                                .flatMap(ch -> ch.createMessage(formatPbBreak(personName, npb)))
                                .then();
                        return reply.then(pbAnnounce);
                    }
                    return reply;
                })
                .orElseGet(() -> {
                    log.debug("Unrecognised message from channel {}: {}", channelId.asString(), content);
                    return Mono.empty();
                });
    }

    @PostConstruct
    public void subscribe() {
        listenToEvents(client.on(MessageCreateEvent.class));
    }

    public void listenToEvents(Flux<MessageCreateEvent> events) {
        events
                .filter(event -> isChannelMonitored(event.getMessage().getChannelId()))
                .filter(event -> isFromConfiguredUser(
                        event.getMessage().getChannelId(),
                        event.getMessage().getAuthor().map(u -> u.getId().asString()).orElse(null)))
                .flatMap(event -> processMessage(
                        event.getMessage().getChannelId(),
                        event.getMessage().getContent(),
                        event.getMessage().getChannel().subscribeOn(Schedulers.boundedElastic())))
                .subscribe(
                        v -> {},
                        error -> log.error("Error in message listener pipeline", error));
    }

    public Mono<Void> replyForOutcome(Mono<MessageChannel> channelMono, SaveOutcome outcome) {
        return switch (outcome) {
            case SAVED -> Mono.empty();
            case WRONG_PUZZLE_NUMBER -> channelMono.flatMap(ch -> ch.createMessage(BotText.MSG_WRONG_PUZZLE_NUMBER)).then();
            case ALREADY_SUBMITTED   -> channelMono.flatMap(ch -> ch.createMessage(BotText.MSG_ALREADY_SUBMITTED)).then();
            case ALREADY_FINISHED    -> channelMono.flatMap(ch -> ch.createMessage(BotText.MSG_FINISHED_LOCKED)).then();
        };
    }

    static String gameLabel(GameResult result) {
        return result.gameLabel();
    }

    static String formatPbBreak(String personName, PbUpdateOutcome.NewPb npb) {
        String gameLabel = npb.gameType().label();
        String newTime = com.wandocorp.nytscorebot.service.TimeFormatter.format(npb.newSeconds());
        String base = npb.dayOfWeek()
                .map(dow -> String.format(BotText.MSG_PB_BROKEN_DOW_FORMAT,
                        personName, gameLabel, capitalize(dow.name()), newTime))
                .orElseGet(() -> String.format(BotText.MSG_PB_BROKEN_FORMAT,
                        personName, gameLabel, newTime));
        if (npb.priorSeconds() != null) {
            base += String.format(BotText.MSG_PB_BROKEN_PRIOR_SUFFIX,
                    com.wandocorp.nytscorebot.service.TimeFormatter.format(npb.priorSeconds()));
        }
        return base;
    }

    private static String capitalize(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}

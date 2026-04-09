package com.wandocorp.nytscorebot.listener;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties.ChannelConfig;
import com.wandocorp.nytscorebot.model.ConnectionsResult;
import com.wandocorp.nytscorebot.model.CrosswordResult;
import com.wandocorp.nytscorebot.model.CrosswordType;
import com.wandocorp.nytscorebot.model.GameResult;
import com.wandocorp.nytscorebot.model.StrandsResult;
import com.wandocorp.nytscorebot.model.WordleResult;
import com.wandocorp.nytscorebot.parser.GameResultParser;
import com.wandocorp.nytscorebot.discord.ResultsChannelService;
import com.wandocorp.nytscorebot.service.SaveOutcome;
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
        return configuredUserId.equals(authorId);
    }

    public Mono<?> processMessage(Snowflake channelId, String content, Mono<MessageChannel> channelMono) {
        String personName = channelPersonMap.get(channelId);
        String discordUserId = channelUserIdMap.get(channelId);
        return parser.parse(content, personName)
                .map(result -> {
                    log.info("Parsed game result for {}: {}", personName, result);
                    SaveOutcome outcome = scoreboardService.saveResult(
                            channelId.asString(), personName, discordUserId, result);
                    if (outcome == SaveOutcome.SAVED) {
                        String contextMessage = String.format(BotText.STATUS_CONTEXT_GAME_SUBMITTED, personName, gameLabel(result));
                        statusChannelService.refresh(contextMessage);
                        resultsChannelService.refreshGame(gameLabel(result));
                    }
                    return replyForOutcome(channelMono, outcome);
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
                .subscribe();
    }

    public Mono<?> replyForOutcome(Mono<MessageChannel> channelMono, SaveOutcome outcome) {
        return switch (outcome) {
            case SAVED -> Mono.empty();
            case WRONG_PUZZLE_NUMBER -> channelMono.flatMap(ch -> ch.createMessage(BotText.MSG_WRONG_PUZZLE_NUMBER));
            case ALREADY_SUBMITTED   -> channelMono.flatMap(ch -> ch.createMessage(BotText.MSG_ALREADY_SUBMITTED));
        };
    }

    static String gameLabel(GameResult result) {
        if (result instanceof WordleResult)      return BotText.GAME_LABEL_WORDLE;
        if (result instanceof ConnectionsResult) return BotText.GAME_LABEL_CONNECTIONS;
        if (result instanceof StrandsResult)     return BotText.GAME_LABEL_STRANDS;
        if (result instanceof CrosswordResult r) {
            return switch (r.getType()) {
                case MINI -> BotText.GAME_LABEL_MINI;
                case MIDI -> BotText.GAME_LABEL_MIDI;
                case MAIN -> BotText.GAME_LABEL_MAIN;
            };
        }
        return BotText.GAME_LABEL_GENERIC;
    }
}

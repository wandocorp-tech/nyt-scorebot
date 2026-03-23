package com.wandocorp.nytscorebot;

import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties.ChannelConfig;
import com.wandocorp.nytscorebot.parser.GameResultParser;
import com.wandocorp.nytscorebot.service.SaveOutcome;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.channel.MessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class MessageListener {

    private static final Logger log = LoggerFactory.getLogger(MessageListener.class);

    private final GatewayDiscordClient client;
    private final GameResultParser parser;
    private final ScoreboardService scoreboardService;
    final Map<Snowflake, String> channelPersonMap;
    final Map<Snowflake, String> channelUserIdMap;

    public MessageListener(GatewayDiscordClient client,
                           DiscordChannelProperties channelProperties,
                           GameResultParser parser,
                           ScoreboardService scoreboardService) {
        this.client = client;
        this.parser = parser;
        this.scoreboardService = scoreboardService;
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

    boolean isChannelMonitored(Snowflake channelId) {
        return channelPersonMap.containsKey(channelId);
    }

    boolean isFromConfiguredUser(Snowflake channelId, String authorId) {
        String configuredUserId = channelUserIdMap.get(channelId);
        return authorId != null && configuredUserId.equals(authorId);
    }

    Mono<?> processMessage(Snowflake channelId, String content, Mono<MessageChannel> channelMono) {
        String personName = channelPersonMap.get(channelId);
        String discordUserId = channelUserIdMap.get(channelId);
        return parser.parse(content, personName)
                .map(result -> {
                    log.info("Parsed game result for {}: {}", personName, result);
                    SaveOutcome outcome = scoreboardService.saveResult(
                            channelId.asString(), personName, discordUserId, result);
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

    void listenToEvents(Flux<MessageCreateEvent> events) {
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

    Mono<?> replyForOutcome(Mono<MessageChannel> channelMono, SaveOutcome outcome) {
        return switch (outcome) {
            case SAVED -> Mono.empty();
            case WRONG_PUZZLE_NUMBER -> channelMono
                    .flatMap(ch -> ch.createMessage("⚠️ That doesn't look like today's puzzle number. " +
                            "Result was not saved."));
            case WRONG_DATE -> channelMono
                    .flatMap(ch -> ch.createMessage("⚠️ That crossword date doesn't match today. " +
                            "Result was not saved."));
            case ALREADY_SUBMITTED -> channelMono
                    .flatMap(ch -> ch.createMessage("ℹ️ You've already submitted this game type today. " +
                            "Result was not saved."));
        };
    }
}

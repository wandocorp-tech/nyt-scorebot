package com.wandocorp.nytscorebot;

import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties.ChannelConfig;
import com.wandocorp.nytscorebot.parser.GameResultParser;
import com.wandocorp.nytscorebot.service.SaveOutcome;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
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
    private final Map<Snowflake, String> channelPersonMap;
    private final Map<Snowflake, String> channelUserIdMap;

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

    @PostConstruct
    public void subscribe() {
        client.on(MessageCreateEvent.class)
                .filter(event -> channelPersonMap.containsKey(event.getMessage().getChannelId()))
                .filter(event -> {
                    String configuredUserId = channelUserIdMap.get(event.getMessage().getChannelId());
                    return event.getMessage().getAuthor()
                            .map(u -> u.getId().asString().equals(configuredUserId))
                            .orElse(false);
                })
                .flatMap(event -> {
                    Snowflake channelId = event.getMessage().getChannelId();
                    String personName = channelPersonMap.get(channelId);
                    String discordUserId = channelUserIdMap.get(channelId);
                    String content = event.getMessage().getContent();
                    Message message = event.getMessage();

                    return parser.parse(content, personName)
                            .map(result -> {
                                log.info("Parsed game result for {}: {}", personName, result);
                                SaveOutcome outcome = scoreboardService.saveResult(
                                        channelId.asString(), personName, discordUserId, result);
                                return replyForOutcome(message, outcome);
                            })
                            .orElseGet(() -> {
                                log.debug("Unrecognised message from channel {}: {}",
                                        channelId.asString(), content);
                                return Mono.empty();
                            });
                })
                .subscribe();
    }

    private Mono<?> replyForOutcome(Message message, SaveOutcome outcome) {
        return switch (outcome) {
            case SAVED -> Mono.empty();
            case WRONG_PUZZLE_NUMBER -> message.getChannel()
                    .flatMap(ch -> ch.createMessage("⚠️ That doesn't look like today's puzzle number. " +
                            "Result was not saved."))
                    .subscribeOn(Schedulers.boundedElastic());
            case WRONG_DATE -> message.getChannel()
                    .flatMap(ch -> ch.createMessage("⚠️ That crossword date doesn't match today. " +
                            "Result was not saved."))
                    .subscribeOn(Schedulers.boundedElastic());
            case ALREADY_SUBMITTED -> message.getChannel()
                    .flatMap(ch -> ch.createMessage("ℹ️ You've already submitted this game type today. " +
                            "Result was not saved."))
                    .subscribeOn(Schedulers.boundedElastic());
        };
    }
}

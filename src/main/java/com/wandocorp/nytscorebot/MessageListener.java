package com.wandocorp.nytscorebot;

import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties.ChannelConfig;
import com.wandocorp.nytscorebot.parser.GameResultParser;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
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

    @PostConstruct
    public void subscribe() {
        client.on(MessageCreateEvent.class)
                .filter(event -> channelPersonMap.containsKey(event.getMessage().getChannelId()))
                .filter(event -> event.getMessage().getAuthor().map(u -> !u.isBot()).orElse(false))
                .flatMap(event -> {
                    Snowflake channelId = event.getMessage().getChannelId();
                    String personName = channelPersonMap.get(channelId);
                    String discordUserId = channelUserIdMap.get(channelId);
                    String content = event.getMessage().getContent();

                    parser.parse(content, personName).ifPresentOrElse(
                            result -> {
                                log.info("Parsed game result for {}: {}", personName, result);
                                scoreboardService.saveResult(channelId.asString(), personName, discordUserId, result);
                            },
                            () -> log.debug("Unrecognised message from channel {}: {}", channelId.asString(), content)
                    );

                    return event.getMessage().getChannel()
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .subscribe();
    }
}


package com.wandocorp.nytscorebot;

import com.wandocorp.nytscorebot.parser.GameResultParser;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class MessageListener {

    private static final Logger log = LoggerFactory.getLogger(MessageListener.class);

    private final GatewayDiscordClient client;
    private final Snowflake channelId;
    private final GameResultParser parser;

    public MessageListener(GatewayDiscordClient client,
                           @Value("${discord.channel-id}") String channelId,
                           GameResultParser parser) {
        this.client = client;
        this.channelId = Snowflake.of(channelId);
        this.parser = parser;
    }

    @PostConstruct
    public void subscribe() {
        client.on(MessageCreateEvent.class)
                .filter(event -> event.getMessage().getChannelId().equals(channelId))
                .filter(event -> event.getMessage().getAuthor().map(u -> !u.isBot()).orElse(false))
                .flatMap(event -> {
                    String content = event.getMessage().getContent();
                    String author = event.getMessage().getAuthor()
                            .map(u -> u.getUsername())
                            .orElse("unknown");

                    parser.parse(content, author).ifPresentOrElse(
                            result -> log.info("Parsed game result: {}", result),
                            () -> log.debug("Unrecognised message from {}: {}", author, content)
                    );

                    return event.getMessage().getChannel();
                })
                .subscribe();
    }
}

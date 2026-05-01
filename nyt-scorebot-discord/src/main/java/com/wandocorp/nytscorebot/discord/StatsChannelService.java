package com.wandocorp.nytscorebot.discord;

import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.channel.MessageChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Posts stats report messages to the configured stats channel.
 *
 * <p>Mirrors the posting pattern in {@link StatusChannelService} but without
 * persistent-message editing — each report is a fresh message.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class StatsChannelService {

    private final GatewayDiscordClient client;
    private final DiscordChannelProperties channelProperties;

    /**
     * Post {@code content} to the configured stats channel.
     *
     * @return a {@link Mono} that completes when the message is sent, or empty if the
     *         stats channel is not configured.
     */
    public Mono<Void> post(String content) {
        String statsChannelId = channelProperties.getStatsChannelId();
        if (statsChannelId == null || statsChannelId.isBlank()) {
            log.warn("Stats channel ID is not configured — skipping report post");
            return Mono.empty();
        }

        return client.getChannelById(Snowflake.of(statsChannelId))
                .cast(MessageChannel.class)
                .flatMap(ch -> ch.createMessage(content))
                .doOnError(e -> log.error("Failed to post stats report to channel {}", statsChannelId, e))
                .onErrorResume(e -> Mono.empty())
                .then();
    }
}

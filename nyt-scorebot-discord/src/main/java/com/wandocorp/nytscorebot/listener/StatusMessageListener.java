package com.wandocorp.nytscorebot.listener;

import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@RequiredArgsConstructor
@Component
public class StatusMessageListener {

    private final GatewayDiscordClient client;
    private final DiscordChannelProperties channelProperties;

    @PostConstruct
    public void subscribe() {
        String statusChannelId = channelProperties.getStatusChannelId();
        if (statusChannelId == null || statusChannelId.isBlank()) return;

        Snowflake statusChannel = Snowflake.of(statusChannelId);
        Snowflake botId = client.getSelfId();

        listenToEvents(client.on(MessageCreateEvent.class), statusChannel, botId);
    }

    void listenToEvents(Flux<MessageCreateEvent> events, Snowflake statusChannel, Snowflake botId) {
        events
                .filter(e -> e.getMessage().getChannelId().equals(statusChannel))
                .filter(e -> e.getMessage().getAuthor()
                        .map(u -> !u.getId().equals(botId))
                        .orElse(true))
                .flatMap(e -> e.getMessage().delete())
                .subscribe();
    }
}

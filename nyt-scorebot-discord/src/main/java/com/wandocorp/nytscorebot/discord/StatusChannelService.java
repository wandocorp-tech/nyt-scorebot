package com.wandocorp.nytscorebot.discord;

import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import com.wandocorp.nytscorebot.service.StatusMessageBuilder;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@RequiredArgsConstructor
@Service
public class StatusChannelService {

    private final GatewayDiscordClient client;
    private final DiscordChannelProperties channelProperties;
    private final ScoreboardService scoreboardService;
    private final AtomicReference<Snowflake> lastMessageId = new AtomicReference<>();

    public void refresh(String contextMessage) {
        String statusChannelId = channelProperties.getStatusChannelId();
        if (statusChannelId == null || statusChannelId.isBlank()) return;

        Snowflake channelSnowflake = Snowflake.of(statusChannelId);
        String table = buildStatusTable(contextMessage);

        deletePreviousMessage(channelSnowflake)
                .then(postNewMessage(channelSnowflake, table))
                .doOnNext(msg -> lastMessageId.set(msg.getId()))
                .subscribe();
    }

    private Mono<Void> deletePreviousMessage(Snowflake channelSnowflake) {
        return Mono.justOrEmpty(lastMessageId.get())
                .flatMap(id -> client.getMessageById(channelSnowflake, id)
                        .flatMap(Message::delete)
                        .onErrorResume(e -> Mono.empty()));
    }

    private Mono<discord4j.core.object.entity.Message> postNewMessage(Snowflake channelSnowflake, String content) {
        return client.getChannelById(channelSnowflake)
                .cast(MessageChannel.class)
                .flatMap(ch -> ch.createMessage(content));
    }

    String buildStatusTable(String contextMessage) {
        List<Scoreboard> scoreboards = scoreboardService.getTodayScoreboards();
        List<String> playerNames = channelProperties.getChannels().stream()
                .map(DiscordChannelProperties.ChannelConfig::getName)
                .sorted()
                .toList();
        return new StatusMessageBuilder(scoreboards, playerNames, contextMessage).build();
    }
}

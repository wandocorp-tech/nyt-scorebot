package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.service.scoreboard.ScoreboardRenderer;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ResultsChannelService {

    private final GatewayDiscordClient client;
    private final DiscordChannelProperties channelProperties;
    private final ScoreboardService scoreboardService;
    private final ScoreboardRenderer scoreboardRenderer;
    private final Map<String, Snowflake> postedMessageIds = new ConcurrentHashMap<>();

    public ResultsChannelService(GatewayDiscordClient client,
                                  DiscordChannelProperties channelProperties,
                                  ScoreboardService scoreboardService,
                                  ScoreboardRenderer scoreboardRenderer) {
        this.client = client;
        this.channelProperties = channelProperties;
        this.scoreboardService = scoreboardService;
        this.scoreboardRenderer = scoreboardRenderer;
    }

    public void refresh() {
        String resultsChannelId = channelProperties.getResultsChannelId();
        if (resultsChannelId == null || resultsChannelId.isBlank()) return;
        if (!scoreboardService.areBothPlayersFinishedToday()) return;

        List<Scoreboard> scoreboards = scoreboardService.getTodayScoreboards();
        List<DiscordChannelProperties.ChannelConfig> channels = channelProperties.getChannels();
        if (channels.size() < 2) return;

        String name1 = channels.get(0).getName();
        String name2 = channels.get(1).getName();

        Map<String, Scoreboard> byName = scoreboards.stream()
                .collect(Collectors.toMap(sb -> sb.getUser().getName(), sb -> sb));

        Scoreboard sb1 = byName.get(name1);
        Scoreboard sb2 = byName.get(name2);

        Snowflake channelSnowflake = Snowflake.of(resultsChannelId);
        Map<String, String> rendered = scoreboardRenderer.renderAll(sb1, name1, sb2, name2);

        for (Map.Entry<String, String> entry : rendered.entrySet()) {
            String gameType = entry.getKey();
            String content = entry.getValue();
            Snowflake existingId = postedMessageIds.get(gameType);
            if (existingId != null) {
                deleteAndRepost(channelSnowflake, existingId, gameType, content);
            } else {
                postMessage(channelSnowflake, gameType, content);
            }
        }
    }

    private void deleteAndRepost(Snowflake channelSnowflake, Snowflake messageId,
                                  String gameType, String content) {
        client.getMessageById(channelSnowflake, messageId)
                .flatMap(Message::delete)
                .onErrorResume(e -> Mono.empty())
                .then(postMessageMono(channelSnowflake, gameType, content))
                .subscribe();
    }

    private void postMessage(Snowflake channelSnowflake, String gameType, String content) {
        postMessageMono(channelSnowflake, gameType, content).subscribe();
    }

    private Mono<Void> postMessageMono(Snowflake channelSnowflake, String gameType, String content) {
        return client.getChannelById(channelSnowflake)
                .cast(MessageChannel.class)
                .flatMap(ch -> ch.createMessage(content))
                .doOnNext(msg -> postedMessageIds.put(gameType, msg.getId()))
                .then();
    }
}

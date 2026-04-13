package com.wandocorp.nytscorebot.discord;

import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import com.wandocorp.nytscorebot.service.StreakService;
import com.wandocorp.nytscorebot.service.scoreboard.ScoreboardRenderer;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class ResultsChannelService {

    private final GatewayDiscordClient client;
    private final DiscordChannelProperties channelProperties;
    private final ScoreboardService scoreboardService;
    private final ScoreboardRenderer scoreboardRenderer;
    private final StreakService streakService;
    private final PuzzleCalendar puzzleCalendar;
    private final Map<String, Snowflake> postedMessageIds = new ConcurrentHashMap<>();
    private final AtomicReference<LocalDate> lastRefreshDate = new AtomicReference<>();

    /** Visible for testing only — pre-populate a posted message ID. */
    void setPostedMessageId(String gameType, Snowflake messageId) {
        postedMessageIds.put(gameType, messageId);
    }

    /** Returns true if a full refresh has been initiated for today (even if async posts are still in flight). */
    public boolean hasPostedResults() {
        LocalDate today = puzzleCalendar.today();
        return today.equals(lastRefreshDate.get());
    }

    public void refresh() {
        RefreshContext ctx = prepareContext();
        if (ctx == null) return;

        lastRefreshDate.set(puzzleCalendar.today());

        Map<String, String> rendered = scoreboardRenderer.renderAll(ctx.sb1, ctx.name1, ctx.sb2, ctx.name2, ctx.streaks);

        for (Map.Entry<String, String> entry : rendered.entrySet()) {
            String gameType = entry.getKey();
            String content = entry.getValue();
            Snowflake existingId = postedMessageIds.get(gameType);
            if (existingId != null) {
                deleteAndRepost(ctx.channelSnowflake, existingId, gameType, content);
            } else {
                postMessage(ctx.channelSnowflake, gameType, content);
            }
        }
    }

    /** Refreshes only a single game type's board in the results channel. */
    public void refreshGame(String gameType) {
        RefreshContext ctx = prepareContext();
        if (ctx == null) return;

        scoreboardRenderer.renderByGameType(gameType, ctx.sb1, ctx.name1, ctx.sb2, ctx.name2, ctx.streaks)
                .ifPresent(content -> {
                    Snowflake existingId = postedMessageIds.get(gameType);
                    if (existingId != null) {
                        deleteAndRepost(ctx.channelSnowflake, existingId, gameType, content);
                    } else {
                        postMessage(ctx.channelSnowflake, gameType, content);
                    }
                });
    }

    private RefreshContext prepareContext() {
        String resultsChannelId = channelProperties.getResultsChannelId();
        if (resultsChannelId == null || resultsChannelId.isBlank()) return null;
        if (!scoreboardService.areBothPlayersFinishedToday()) return null;

        List<Scoreboard> scoreboards = scoreboardService.getTodayScoreboards();
        List<DiscordChannelProperties.ChannelConfig> channels = channelProperties.getChannels();
        if (channels.size() < 2) return null;

        String name1 = channels.get(0).getName();
        String name2 = channels.get(1).getName();

        Map<String, Scoreboard> byName = scoreboards.stream()
                .collect(Collectors.toMap(sb -> sb.getUser().getName(), sb -> sb));

        Scoreboard sb1 = byName.get(name1);
        Scoreboard sb2 = byName.get(name2);

        Snowflake channelSnowflake = Snowflake.of(resultsChannelId);
        Map<String, Map<String, Integer>> streaks = buildStreakMap(sb1, name1, sb2, name2);

        return new RefreshContext(channelSnowflake, sb1, name1, sb2, name2, streaks);
    }

    private record RefreshContext(Snowflake channelSnowflake,
                                   Scoreboard sb1, String name1,
                                   Scoreboard sb2, String name2,
                                   Map<String, Map<String, Integer>> streaks) {}

    private void deleteAndRepost(Snowflake channelSnowflake, Snowflake messageId,
                                  String gameType, String content) {
        client.getMessageById(channelSnowflake, messageId)
                .flatMap(Message::delete)
                .onErrorResume(e -> Mono.empty())
                .then(postMessageMono(channelSnowflake, gameType, content))
                .subscribe(
                        v -> {},
                        error -> log.error("Error reposting results for {}", gameType, error));
    }

    private void postMessage(Snowflake channelSnowflake, String gameType, String content) {
        postMessageMono(channelSnowflake, gameType, content)
                .subscribe(
                        v -> {},
                        error -> log.error("Error posting results for {}", gameType, error));
    }

    private Mono<Void> postMessageMono(Snowflake channelSnowflake, String gameType, String content) {
        return client.getChannelById(channelSnowflake)
                .cast(MessageChannel.class)
                .flatMap(ch -> ch.createMessage(content))
                .doOnNext(msg -> postedMessageIds.put(gameType, msg.getId()))
                .then();
    }

    private Map<String, Map<String, Integer>> buildStreakMap(Scoreboard sb1, String name1,
                                                              Scoreboard sb2, String name2) {
        Map<String, Map<String, Integer>> streaks = new HashMap<>();
        if (sb1 != null) streaks.put(name1, streakService.getStreaks(sb1.getUser()));
        if (sb2 != null) streaks.put(name2, streakService.getStreaks(sb2.getUser()));
        return streaks;
    }
}

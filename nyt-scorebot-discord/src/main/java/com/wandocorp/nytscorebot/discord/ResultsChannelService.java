package com.wandocorp.nytscorebot.discord;

import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.service.CrosswordWinStreakService;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import com.wandocorp.nytscorebot.service.StreakService;
import com.wandocorp.nytscorebot.service.WinStreakService;
import com.wandocorp.nytscorebot.service.WinStreakSummaryBuilder;
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
    private final WinStreakService winStreakService;
    private final CrosswordWinStreakService crosswordWinStreakService;
    private final PuzzleCalendar puzzleCalendar;
    private final Map<String, Snowflake> postedMessageIds = new ConcurrentHashMap<>();
    private final AtomicReference<LocalDate> lastRefreshDate = new AtomicReference<>();

    /** Pseudo-game-type slot used to track the win streak summary message ID. */
    public static final String WIN_STREAK_SUMMARY_SLOT = "__win_streak_summary__";

    /** Visible for testing only — pre-populate a posted message ID. */
    void setPostedMessageId(String gameType, Snowflake messageId) {
        postedMessageIds.put(gameType, messageId);
    }

    /** Returns the posted message ID for a given slot, or null if none has been posted yet. */
    public Snowflake getPostedMessageId(String slot) {
        return postedMessageIds.get(slot);
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

        // Recompute crossword win streaks before rendering so the summary reflects today.
        crosswordWinStreakService.updateAll(ctx.sb1, ctx.name1, ctx.sb2, ctx.name2, puzzleCalendar.today());

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

        postOrEditWinStreakSummary(ctx);
    }

    /** Refreshes only a single game type's board in the results channel. */
    public void refreshGame(String gameType) {
        RefreshContext ctx = prepareContext();
        if (ctx == null) return;

        // For crossword games, recompute the win streak before re-rendering so any
        // flag change (e.g. /duo) is reflected in both the scoreboard and the summary.
        GameType crossword = crosswordGameTypeFor(gameType);
        if (crossword != null) {
            crosswordWinStreakService.updateGame(crossword, ctx.sb1, ctx.name1, ctx.sb2, ctx.name2,
                    puzzleCalendar.today());
        }

        scoreboardRenderer.renderByGameType(gameType, ctx.sb1, ctx.name1, ctx.sb2, ctx.name2, ctx.streaks)
                .ifPresent(content -> {
                    Snowflake existingId = postedMessageIds.get(gameType);
                    if (existingId != null) {
                        deleteAndRepost(ctx.channelSnowflake, existingId, gameType, content);
                    } else {
                        postMessage(ctx.channelSnowflake, gameType, content);
                    }
                });

        if (crossword != null) {
            postOrEditWinStreakSummary(ctx);
        }
    }

    private static GameType crosswordGameTypeFor(String gameLabel) {
        GameType gt = GameType.fromLabel(gameLabel);
        if (gt == GameType.MINI_CROSSWORD || gt == GameType.MIDI_CROSSWORD || gt == GameType.MAIN_CROSSWORD) {
            return gt;
        }
        return null;
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
        Map<String, Map<GameType, Integer>> streaks = buildStreakMap(sb1, name1, sb2, name2);

        return new RefreshContext(channelSnowflake, sb1, name1, sb2, name2, streaks);
    }

    private record RefreshContext(Snowflake channelSnowflake,
                                   Scoreboard sb1, String name1,
                                   Scoreboard sb2, String name2,
                                   Map<String, Map<GameType, Integer>> streaks) {}

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

    private Map<String, Map<GameType, Integer>> buildStreakMap(Scoreboard sb1, String name1,
                                                              Scoreboard sb2, String name2) {
        Map<String, Map<GameType, Integer>> streaks = new HashMap<>();
        if (sb1 != null) streaks.put(name1, streakService.getStreaks(sb1.getUser()));
        if (sb2 != null) streaks.put(name2, streakService.getStreaks(sb2.getUser()));
        return streaks;
    }

    private void postOrEditWinStreakSummary(RefreshContext ctx) {
        if (ctx.sb1 == null || ctx.sb2 == null) return;
        User u1 = ctx.sb1.getUser();
        User u2 = ctx.sb2.getUser();
        Map<User, Map<GameType, Integer>> winStreaks = new HashMap<>();
        winStreaks.put(u1, winStreakService.getStreaks(u1));
        winStreaks.put(u2, winStreakService.getStreaks(u2));

        String content = WinStreakSummaryBuilder.build(u1, ctx.name1, u2, ctx.name2, winStreaks);

        Snowflake existingId = postedMessageIds.get(WIN_STREAK_SUMMARY_SLOT);
        if (existingId != null) {
            editSummaryMessage(ctx.channelSnowflake, existingId, content);
        } else {
            postMessage(ctx.channelSnowflake, WIN_STREAK_SUMMARY_SLOT, content);
        }
    }

    private void editSummaryMessage(Snowflake channelSnowflake, Snowflake messageId, String content) {
        client.getMessageById(channelSnowflake, messageId)
                .flatMap(msg -> msg.edit(spec -> spec.setContent(content)))
                .subscribe(
                        v -> {},
                        error -> log.error("Error editing win streak summary message", error));
    }
}

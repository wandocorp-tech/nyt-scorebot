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

    private final DiscordChannelProperties channelProperties;
    private final ScoreboardService scoreboardService;
    private final ScoreboardRenderer scoreboardRenderer;
    private final StreakService streakService;
    private final WinStreakService winStreakService;
    private final CrosswordWinStreakService crosswordWinStreakService;
    private final PuzzleCalendar puzzleCalendar;
    private final MessageSlotWriter slotWriter;
    private final Map<String, Snowflake> postedMessageIds = new ConcurrentHashMap<>();
    /**
     * Per-slot in-flight write chain. Each {@link #writeSlot(Snowflake, String, String)} call
     * appends to the existing chain so consecutive writes for the same slot are serialised
     * — preventing a duplicate post when a second write arrives before the first has
     * completed and recorded its message id.
     */
    private final Map<String, Mono<Snowflake>> slotChains = new ConcurrentHashMap<>();
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
        RefreshContext ctx = prepareContext(puzzleCalendar.today(), false);
        if (ctx == null) return;

        rolloverIfNewDay();
        lastRefreshDate.set(puzzleCalendar.today());

        // Recompute crossword win streaks before rendering so the summary reflects today.
        crosswordWinStreakService.updateAll(ctx.sb1, ctx.name1, ctx.sb2, ctx.name2, puzzleCalendar.today());

        var pbLookup = scoreboardService.buildCrosswordPbLookup(ctx.name1, ctx.name2, puzzleCalendar.today());
        Map<String, String> rendered = scoreboardRenderer.renderAll(ctx.sb1, ctx.name1, ctx.sb2, ctx.name2, ctx.streaks, pbLookup);

        for (Map.Entry<String, String> entry : rendered.entrySet()) {
            writeSlot(ctx.channelSnowflake, entry.getKey(), entry.getValue());
        }

        postOrEditWinStreakSummary(ctx);
    }

    /**
     * Forces all boards to be posted for the given date, regardless of whether both players
     * have finished. Used by the midnight job to publish end-of-day results.
     */
    public void forceRefreshForDate(LocalDate date) {
        RefreshContext ctx = prepareContext(date, true);
        if (ctx == null) return;

        lastRefreshDate.set(date);

        crosswordWinStreakService.updateAll(ctx.sb1, ctx.name1, ctx.sb2, ctx.name2, date);

        var pbLookup = scoreboardService.buildCrosswordPbLookup(ctx.name1, ctx.name2, date);
        Map<String, String> rendered = scoreboardRenderer.renderAll(ctx.sb1, ctx.name1, ctx.sb2, ctx.name2, ctx.streaks, pbLookup);
        for (Map.Entry<String, String> entry : rendered.entrySet()) {
            writeSlot(ctx.channelSnowflake, entry.getKey(), entry.getValue());
        }

        postOrEditWinStreakSummary(ctx);
    }

    /** Refreshes only a single game type's board in the results channel. */
    public void refreshGame(String gameType) {
        RefreshContext ctx = prepareContext(puzzleCalendar.today(), false);
        if (ctx == null) return;

        rolloverIfNewDay();

        // For crossword games, recompute the win streak before re-rendering so any
        // flag change (e.g. /duo) is reflected in both the scoreboard and the summary.
        GameType crossword = crosswordGameTypeFor(gameType);
        if (crossword != null) {
            crosswordWinStreakService.updateGame(crossword, ctx.sb1, ctx.name1, ctx.sb2, ctx.name2,
                    puzzleCalendar.today());
        }

        var pbLookup = scoreboardService.buildCrosswordPbLookup(ctx.name1, ctx.name2, puzzleCalendar.today());
        scoreboardRenderer.renderByGameType(gameType, ctx.sb1, ctx.name1, ctx.sb2, ctx.name2, ctx.streaks, pbLookup)
                .ifPresent(content -> writeSlot(ctx.channelSnowflake, gameType, content));

        if (crossword != null) {
            postOrEditWinStreakSummary(ctx);
        }
    }

    /** Returns true if results have been posted for the given date. */
    public boolean hasPostedResultsForDate(LocalDate date) {
        return date.equals(lastRefreshDate.get());
    }

    private static GameType crosswordGameTypeFor(String gameLabel) {
        GameType gt = GameType.fromLabel(gameLabel);
        if (gt == GameType.MINI_CROSSWORD || gt == GameType.MIDI_CROSSWORD || gt == GameType.MAIN_CROSSWORD) {
            return gt;
        }
        return null;
    }

    /**
     * Clears the posted-message-id tracking when the day has rolled over since the last refresh,
     * so the first refresh on a new day posts fresh scoreboards rather than editing yesterday's.
     * The persistent status board (handled by {@link StatusChannelService}) is intentionally
     * unaffected — it is always edited in place.
     */
    private void rolloverIfNewDay() {
        LocalDate today = puzzleCalendar.today();
        LocalDate prev = lastRefreshDate.get();
        if (prev != null && !prev.equals(today)) {
            log.info("Day rolled over from {} to {} — clearing tracked results message ids", prev, today);
            postedMessageIds.clear();
            slotChains.clear();
        }
    }

    private RefreshContext prepareContext(LocalDate date, boolean force) {
        String resultsChannelId = channelProperties.getResultsChannelId();
        if (resultsChannelId == null || resultsChannelId.isBlank()) return null;
        if (!force && !scoreboardService.areBothPlayersFinishedToday()) return null;

        List<Scoreboard> scoreboards = scoreboardService.getScoreboardsForDate(date);
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

    private void writeSlot(Snowflake channelSnowflake, String slot, String content) {
        Mono<Snowflake> chained = slotChains.compute(slot, (k, prev) -> {
            Mono<Snowflake> prevId = (prev == null)
                    ? Mono.justOrEmpty(postedMessageIds.get(slot))
                    : prev.onErrorResume(e -> Mono.empty());
            return prevId
                    .flatMap(id -> slotWriter.editOrPost(channelSnowflake, id, content))
                    .switchIfEmpty(Mono.defer(() -> slotWriter.editOrPost(channelSnowflake, null, content)))
                    .cache();
        });
        chained.subscribe(
                id -> postedMessageIds.put(slot, id),
                error -> log.error("Error writing results for {}", slot, error));
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
        writeSlot(ctx.channelSnowflake, WIN_STREAK_SUMMARY_SLOT, content);
    }
}

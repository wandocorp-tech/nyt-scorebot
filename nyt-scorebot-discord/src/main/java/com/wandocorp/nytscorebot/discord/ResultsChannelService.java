package com.wandocorp.nytscorebot.discord;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.entity.Scoreboard;
import com.wandocorp.nytscorebot.entity.User;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.service.CrosswordWinStreakService;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import com.wandocorp.nytscorebot.service.StreakService;
import com.wandocorp.nytscorebot.service.TripleCrownService;
import com.wandocorp.nytscorebot.service.WinStreakService;
import com.wandocorp.nytscorebot.service.WinStreakSummaryBuilder;
import com.wandocorp.nytscorebot.service.scoreboard.ComparisonOutcome;
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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

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
    private final TripleCrownService tripleCrownService;
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

    /** Display name currently holding a posted triple crown, or null when none is posted. */
    private final AtomicReference<String> crownedPlayer = new AtomicReference<>();

    /** Pseudo-game-type slot used to track the win streak summary message ID. */
    public static final String WIN_STREAK_SUMMARY_SLOT = "__win_streak_summary__";

    /**
     * Pseudo-game-type slot used to track the triple crown celebration message ID.
     * Unlike every other slot this one is <em>conditional</em>: when the crown is no longer
     * earned the message is deleted rather than edited, so a revoked crown leaves no trace.
     */
    public static final String TRIPLE_CROWN_SLOT = "__triple_crown__";

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
        Map<GameType, ComparisonOutcome> outcomes = crosswordWinStreakService.updateAll(
                ctx.sb1, ctx.name1, ctx.sb2, ctx.name2, puzzleCalendar.today());

        Map<String, String> rendered = scoreboardRenderer.renderAll(ctx.sb1, ctx.name1, ctx.sb2, ctx.name2, ctx.streaks);
        warnIfNothingRendered(rendered, puzzleCalendar.today(), ctx);

        for (Map.Entry<String, String> entry : rendered.entrySet()) {
            writeSlot(ctx.channelSnowflake, entry.getKey(), entry.getValue());
        }

        postOrEditWinStreakSummary(ctx);
        postOrRevokeTripleCrown(ctx, outcomes);
    }

    /**
     * Forces all boards to be posted for the given date, regardless of whether both players
     * have finished. Used by the midnight job to publish end-of-day results.
     */
    public void forceRefreshForDate(LocalDate date) {
        RefreshContext ctx = prepareContext(date, true);
        if (ctx == null) return;

        lastRefreshDate.set(date);

        Map<GameType, ComparisonOutcome> outcomes = crosswordWinStreakService.updateAll(
                ctx.sb1, ctx.name1, ctx.sb2, ctx.name2, date);

        Map<String, String> rendered = scoreboardRenderer.renderAll(ctx.sb1, ctx.name1, ctx.sb2, ctx.name2, ctx.streaks);
        warnIfNothingRendered(rendered, date, ctx);

        for (Map.Entry<String, String> entry : rendered.entrySet()) {
            writeSlot(ctx.channelSnowflake, entry.getKey(), entry.getValue());
        }

        postOrEditWinStreakSummary(ctx);
        postOrRevokeTripleCrown(ctx, outcomes);
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

        scoreboardRenderer.renderByGameType(gameType, ctx.sb1, ctx.name1, ctx.sb2, ctx.name2, ctx.streaks)
                .ifPresent(content -> writeSlot(ctx.channelSnowflake, gameType, content));

        if (crossword != null) {
            postOrEditWinStreakSummary(ctx);
            // A flag change can make or break a sweep, so re-evaluate the crown from the full
            // picture — without mutating the other two games' streaks.
            postOrRevokeTripleCrown(ctx,
                    crosswordWinStreakService.computeOutcomes(ctx.sb1, ctx.name1, ctx.sb2, ctx.name2));
        }
    }

    /** Returns true if results have been posted for the given date. */
    public boolean hasPostedResultsForDate(LocalDate date) {
        return date.equals(lastRefreshDate.get());
    }

    /**
     * Re-evaluates the triple crown for a date without touching the game boards.
     *
     * <p>Invoked by the midnight rollover so a sweep that only becomes complete once forfeits
     * are applied is still recognised, even when the force-post step was skipped because the
     * boards had already been published during the day.
     */
    public void refreshTripleCrownForDate(LocalDate date) {
        RefreshContext ctx = prepareContext(date, true);
        if (ctx == null) return;
        postOrRevokeTripleCrown(ctx,
                crosswordWinStreakService.computeOutcomes(ctx.sb1, ctx.name1, ctx.sb2, ctx.name2));
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
            crownedPlayer.set(null);
        }
    }

    private RefreshContext prepareContext(LocalDate date, boolean force) {
        String resultsChannelId = channelProperties.getResultsChannelId();
        if (resultsChannelId == null || resultsChannelId.isBlank()) {
            log.debug("Skipping results refresh for {} — no results channel configured", date);
            return null;
        }
        if (!force && !scoreboardService.areBothPlayersFinishedToday()) {
            log.debug("Skipping results refresh for {} — both players have not finished", date);
            return null;
        }

        List<Scoreboard> scoreboards = scoreboardService.getScoreboardsForDate(date);
        List<DiscordChannelProperties.ChannelConfig> channels = channelProperties.getChannels();
        if (channels.size() < 2) {
            log.warn("Skipping results refresh for {} — {} channel(s) configured, need at least 2",
                    date, channels.size());
            return null;
        }

        String name1 = channels.get(0).getName();
        String name2 = channels.get(1).getName();

        Scoreboard sb1 = findScoreboardFor(scoreboards, channels.get(0));
        Scoreboard sb2 = findScoreboardFor(scoreboards, channels.get(1));

        Snowflake channelSnowflake = Snowflake.of(resultsChannelId);
        Map<String, Map<GameType, Integer>> streaks = buildStreakMap(sb1, name1, sb2, name2);

        return new RefreshContext(channelSnowflake, sb1, name1, sb2, name2, streaks, scoreboards.size());
    }

    /**
     * Resolves a channel's player scoreboard by the immutable {@code channel_id} that
     * {@link com.wandocorp.nytscorebot.service.ScoreboardService} uses as the user's identity.
     *
     * <p>Deliberately <em>not</em> keyed on the display name: {@code app_user.name} is written
     * from the mutable {@code discord.channels[n].name} property, so a rename would silently
     * yield no match, render nothing, and post nothing.
     */
    private Scoreboard findScoreboardFor(List<Scoreboard> scoreboards,
                                         DiscordChannelProperties.ChannelConfig channel) {
        for (Scoreboard sb : scoreboards) {
            User user = sb.getUser();
            if (user == null) continue;
            if (channel.getId() != null && channel.getId().equals(user.getChannelId())) return sb;
            if (channel.getUserId() != null && channel.getUserId().equals(user.getDiscordUserId())) return sb;
        }
        return null;
    }

    /**
     * Logs the signature of a silent no-op: scoreboards exist for the date but every game
     * rendered empty, so no Discord write is issued at all.
     */
    private void warnIfNothingRendered(Map<String, String> rendered, LocalDate date, RefreshContext ctx) {
        if (rendered.isEmpty() && ctx.scoreboardCount > 0) {
            log.warn("Rendered no results for {} despite finding {} scoreboard(s) — "
                            + "resolved players: {}={}, {}={}",
                    date, ctx.scoreboardCount,
                    ctx.name1, ctx.sb1 != null, ctx.name2, ctx.sb2 != null);
        }
    }

    private record RefreshContext(Snowflake channelSnowflake,
                                   Scoreboard sb1, String name1,
                                   Scoreboard sb2, String name2,
                                   Map<String, Map<GameType, Integer>> streaks,
                                   int scoreboardCount) {}

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

    /**
     * Serialised delete for a conditional slot. Chained through the same per-slot queue as
     * {@link #writeSlot} so a delete cannot overtake an in-flight post and orphan the message.
     */
    private void deleteSlot(Snowflake channelSnowflake, String slot) {
        Mono<Snowflake> prev = slotChains.get(slot);
        Snowflake tracked = postedMessageIds.get(slot);
        if (prev == null && tracked == null) return;

        Mono<Snowflake> resolved = (prev != null)
                ? prev.onErrorResume(e -> Mono.empty())
                : Mono.just(tracked);

        resolved.flatMap(id -> slotWriter.delete(channelSnowflake, id).thenReturn(id))
                .subscribe(
                        id -> {
                            postedMessageIds.remove(slot);
                            slotChains.remove(slot);
                        },
                        error -> log.error("Error deleting message for slot {}", slot, error),
                        () -> {
                            postedMessageIds.remove(slot);
                            slotChains.remove(slot);
                        });
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

    /**
     * Posts the triple crown celebration when one player has swept all three crosswords, or
     * deletes a previously posted celebration when the sweep no longer stands (for example
     * after a {@code /duo}, {@code /check}, or {@code /lookups} flag change).
     *
     * <p>The crown is the last message of the results block, posted after the win streak summary.
     */
    private void postOrRevokeTripleCrown(RefreshContext ctx, Map<GameType, ComparisonOutcome> outcomes) {
        Optional<String> winner = tripleCrownService.detect(outcomes, ctx.sb1, ctx.name1, ctx.sb2, ctx.name2);

        if (winner.isEmpty()) {
            if (postedMessageIds.containsKey(TRIPLE_CROWN_SLOT) || slotChains.containsKey(TRIPLE_CROWN_SLOT)) {
                log.info("Triple crown revoked — deleting celebration message");
                deleteSlot(ctx.channelSnowflake, TRIPLE_CROWN_SLOT);
            }
            crownedPlayer.set(null);
            return;
        }

        String name = winner.get();
        if (name.equals(crownedPlayer.get())) return;

        log.info("Triple crown awarded to {}", name);
        crownedPlayer.set(name);
        writeSlot(ctx.channelSnowflake, TRIPLE_CROWN_SLOT, BotText.TRIPLE_CROWN.formatted(name));
    }
}

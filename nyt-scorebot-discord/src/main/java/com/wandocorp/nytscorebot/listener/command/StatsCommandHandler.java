package com.wandocorp.nytscorebot.listener.command;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.StatsProperties;
import com.wandocorp.nytscorebot.discord.StatsChannelService;
import com.wandocorp.nytscorebot.listener.StatsConfirmationButtonListener;
import com.wandocorp.nytscorebot.listener.StatsConfirmationTracker;
import com.wandocorp.nytscorebot.repository.ScoreboardRepository;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.service.stats.CrosswordStatsReportBuilder;
import com.wandocorp.nytscorebot.service.stats.CrosswordStatsService;
import com.wandocorp.nytscorebot.service.stats.GameTypeFilter;
import com.wandocorp.nytscorebot.service.stats.StatsWindowBeforeAnchorException;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Handles the {@code /stats} slash command.
 *
 * <p>For small windows (≤ 90-day custom, or {@code week}/{@code month}), defers the reply,
 * computes and posts the report to the stats channel, then acknowledges ephemerally.
 *
 * <p>For large windows ({@code year}, {@code all-time}, or {@code custom} > 90 days),
 * replies with an ephemeral confirmation prompt (two buttons), then schedules a 15-second
 * auto-cancel timer. The {@link StatsConfirmationButtonListener} handles the actual
 * computation when the user clicks {@code Run report}.
 */
@Slf4j
@Component
public class StatsCommandHandler implements SlashCommandHandler {

    private final CrosswordStatsService statsService;
    private final CrosswordStatsReportBuilder reportBuilder;
    private final StatsChannelService statsChannelService;
    private final DiscordChannelProperties channelProperties;
    private final StatsProperties statsProperties;
    private final StatsConfirmationTracker tracker;
    private final PuzzleCalendar puzzleCalendar;
    private final ScoreboardRepository scoreboardRepository;
    private final Scheduler scheduler;

    @Autowired
    public StatsCommandHandler(CrosswordStatsService statsService,
                                CrosswordStatsReportBuilder reportBuilder,
                                StatsChannelService statsChannelService,
                                DiscordChannelProperties channelProperties,
                                StatsProperties statsProperties,
                                StatsConfirmationTracker tracker,
                                PuzzleCalendar puzzleCalendar,
                                ScoreboardRepository scoreboardRepository) {
        this(statsService, reportBuilder, statsChannelService, channelProperties, statsProperties,
                tracker, puzzleCalendar, scoreboardRepository, Schedulers.parallel());
    }

    /** Visible for tests — allows injecting a custom scheduler (e.g. VirtualTimeScheduler). */
    StatsCommandHandler(CrosswordStatsService statsService,
                         CrosswordStatsReportBuilder reportBuilder,
                         StatsChannelService statsChannelService,
                         DiscordChannelProperties channelProperties,
                         StatsProperties statsProperties,
                         StatsConfirmationTracker tracker,
                         PuzzleCalendar puzzleCalendar,
                         ScoreboardRepository scoreboardRepository,
                         Scheduler scheduler) {
        this.statsService        = statsService;
        this.reportBuilder       = reportBuilder;
        this.statsChannelService = statsChannelService;
        this.channelProperties   = channelProperties;
        this.statsProperties     = statsProperties;
        this.tracker             = tracker;
        this.puzzleCalendar      = puzzleCalendar;
        this.scoreboardRepository = scoreboardRepository;
        this.scheduler           = scheduler;
    }

    @Override
    public String commandName() {
        return BotText.CMD_STATS;
    }

    @Override
    public Mono<Void> handle(ChatInputInteractionEvent event) {
        // ── Feature guard ─────────────────────────────────────────────────────
        if (!statsProperties.isEnabled()) {
            return replyError(event, BotText.STATS_ERR_ANCHOR_UNSET);
        }
        String statsChannelId = channelProperties.getStatsChannelId();
        if (statsChannelId == null || statsChannelId.isBlank()) {
            return replyError(event, BotText.STATS_ERR_CHANNEL_UNSET);
        }

        // ── Extract options ───────────────────────────────────────────────────
        String game   = getStringOption(event, BotText.CMD_STATS_GAME_OPTION).orElse(BotText.STATS_GAME_ALL);
        String period = getStringOption(event, BotText.CMD_STATS_PERIOD_OPTION).orElse(BotText.STATS_PERIOD_WEEK);
        Optional<String> fromOpt = getStringOption(event, BotText.CMD_STATS_FROM_OPTION);
        Optional<String> toOpt   = getStringOption(event, BotText.CMD_STATS_TO_OPTION);

        boolean isCustom = BotText.STATS_PERIOD_CUSTOM.equals(period);

        // ── Validation ────────────────────────────────────────────────────────
        if (isCustom && (fromOpt.isEmpty() || toOpt.isEmpty())) {
            return replyError(event, BotText.STATS_ERR_CUSTOM_MISSING_DATES);
        }
        if (!isCustom && (fromOpt.isPresent() || toOpt.isPresent())) {
            return replyError(event, BotText.STATS_ERR_DATES_ON_NON_CUSTOM);
        }

        LocalDate today = puzzleCalendar.today();
        LocalDate from;
        LocalDate to;

        if (isCustom) {
            try {
                from = LocalDate.parse(fromOpt.get());
                to   = LocalDate.parse(toOpt.get());
            } catch (DateTimeParseException e) {
                return replyError(event, BotText.STATS_ERR_INVALID_DATE);
            }
            if (from.isAfter(to)) {
                return replyError(event, BotText.STATS_ERR_FROM_AFTER_TO);
            }
            if (to.isAfter(today.minusDays(1))) {
                return replyError(event, BotText.STATS_ERR_TO_IN_FUTURE);
            }
            LocalDate anchor = statsProperties.getAnchorDate();
            if (anchor != null && to.isBefore(anchor)) {
                return replyError(event,
                        String.format(BotText.STATS_ERR_WINDOW_BEFORE_ANCHOR, anchor));
            }
        } else {
            LocalDate[] window = StatsConfirmationButtonListener.resolveWindow(
                    period, today, statsProperties.getAnchorDate());
            from = window[0];
            to   = window[1];
        }

        GameTypeFilter filter = StatsConfirmationButtonListener.gameFilter(game);
        String label          = StatsConfirmationButtonListener.periodLabel(period);
        boolean isLarge = isLargePeriod(period, from, to);

        if (isLarge) {
            return postConfirmationPrompt(event, game, period,
                    fromOpt.orElse(""), toOpt.orElse(""));
        }

        // ── Small window: defer, compute, post ────────────────────────────────
        return event.deferReply().withEphemeral(true)
                .then(Mono.fromCallable(() -> {
                    var report = statsService.compute(filter, from, to);
                    return reportBuilder.render(report, label);
                }))
                .flatMap(content -> statsChannelService.post(content)
                        .thenReturn(content))
                .flatMap(ignored -> event.editReply(
                        String.format(BotText.STATS_POSTED, statsChannelId)).then())
                .onErrorResume(StatsWindowBeforeAnchorException.class, ex ->
                        event.editReply(String.format(
                                BotText.STATS_ERR_WINDOW_BEFORE_ANCHOR, ex.getAnchor())).then())
                .onErrorResume(e -> {
                    log.error("Error computing stats for /stats command", e);
                    return event.editReply("⚠️ An error occurred while computing the report.").then();
                });
    }

    // ── Confirmation prompt ───────────────────────────────────────────────────

    private Mono<Void> postConfirmationPrompt(ChatInputInteractionEvent event,
                                               String game, String period,
                                               String fromStr, String toStr) {
        String paramKey        = game + ":" + period + ":" + fromStr + ":" + toStr;
        String confirmCustomId = "stats-confirm:" + paramKey;
        String cancelCustomId  = "stats-cancel:"  + paramKey;

        ActionRow buttons = ActionRow.of(
                Button.primary(confirmCustomId, BotText.STATS_CONFIRM_RUN),
                Button.secondary(cancelCustomId, BotText.STATS_CONFIRM_CANCEL)
        );

        return event.reply()
                .withEphemeral(true)
                .withContent(BotText.STATS_CONFIRM_PROMPT)
                .withComponents(buttons)
                .then(Mono.fromRunnable(() -> scheduleTimeout(event, paramKey)));
    }

    private void scheduleTimeout(ChatInputInteractionEvent event, String paramKey) {
        Disposable timer = Mono.delay(Duration.ofSeconds(15), scheduler)
                .doOnNext(i -> tracker.remove(paramKey))
                .flatMap(i -> event.editReply(BotText.STATS_CONFIRM_TIMEOUT).then())
                .subscribe(v -> {}, e -> log.warn("Stats confirm timer error for key {}: {}",
                        paramKey, e.getMessage()));
        tracker.register(paramKey, timer);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    static boolean isLargePeriod(String period, LocalDate from, LocalDate to) {
        if (BotText.STATS_PERIOD_YEAR.equals(period) || BotText.STATS_PERIOD_ALL_TIME.equals(period)) {
            return true;
        }
        if (BotText.STATS_PERIOD_CUSTOM.equals(period)) {
            return ChronoUnit.DAYS.between(from, to) > 90;
        }
        return false;
    }

    private static Mono<Void> replyError(ChatInputInteractionEvent event, String message) {
        return event.reply().withEphemeral(true).withContent(message).then();
    }

    private static Optional<String> getStringOption(ChatInputInteractionEvent event, String name) {
        return event.getOption(name)
                .flatMap(ApplicationCommandInteractionOption::getValue)
                .map(ApplicationCommandInteractionOptionValue::asString);
    }
}

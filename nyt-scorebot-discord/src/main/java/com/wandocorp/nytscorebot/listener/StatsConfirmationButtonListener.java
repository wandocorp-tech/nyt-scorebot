package com.wandocorp.nytscorebot.listener;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.StatsProperties;
import com.wandocorp.nytscorebot.discord.StatsChannelService;
import com.wandocorp.nytscorebot.repository.ScoreboardRepository;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.service.stats.CrosswordStatsReportBuilder;
import com.wandocorp.nytscorebot.service.stats.CrosswordStatsService;
import com.wandocorp.nytscorebot.service.stats.GameTypeFilter;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Handles button clicks for the {@code /stats} large-window confirmation prompt.
 *
 * <p>Custom IDs are in the form {@code stats-confirm:<game>:<period>:<from?>:<to?>} and
 * {@code stats-cancel:<game>:<period>:<from?>:<to?>}, where {@code from} and {@code to}
 * are empty strings for non-custom periods.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class StatsConfirmationButtonListener {

    private static final String CONFIRM_PREFIX = "stats-confirm:";
    private static final String CANCEL_PREFIX  = "stats-cancel:";

    private final GatewayDiscordClient client;
    private final StatsConfirmationTracker tracker;
    private final CrosswordStatsService statsService;
    private final CrosswordStatsReportBuilder reportBuilder;
    private final StatsChannelService statsChannelService;
    private final DiscordChannelProperties channelProperties;
    private final PuzzleCalendar puzzleCalendar;
    private final StatsProperties statsProperties;
    private final ScoreboardRepository scoreboardRepository;

    @PostConstruct
    public void subscribe() {
        client.on(ButtonInteractionEvent.class)
                .filter(e -> e.getCustomId().startsWith(CONFIRM_PREFIX)
                          || e.getCustomId().startsWith(CANCEL_PREFIX))
                .flatMap(this::handleButton)
                .subscribe(v -> {}, e -> log.error("Unhandled error in stats button listener", e));
    }

    Mono<Void> handleButton(ButtonInteractionEvent event) {
        String customId = event.getCustomId();
        boolean isConfirm = customId.startsWith(CONFIRM_PREFIX);
        String paramKey = customId.substring(isConfirm ? CONFIRM_PREFIX.length() : CANCEL_PREFIX.length());

        Optional<Disposable> timerOpt = tracker.remove(paramKey);
        if (timerOpt.isEmpty()) {
            // Timer already fired — prompt is expired.
            return event.reply()
                    .withEphemeral(true)
                    .withContent(BotText.STATS_CONFIRM_EXPIRED)
                    .then();
        }
        timerOpt.get().dispose();

        if (!isConfirm) {
            return event.deferEdit()
                    .then(event.editReply(BotText.STATS_CONFIRM_CANCELLED))
                    .then();
        }

        // Confirm: compute and post.
        String[] parts = paramKey.split(":", 4);
        String game   = parts[0];
        String period = parts[1];
        String fromStr = parts.length > 2 ? parts[2] : "";
        String toStr   = parts.length > 3 ? parts[3] : "";

        return event.deferEdit()
                .then(event.editReply(BotText.STATS_CONFIRM_COMPUTING))
                .then(Mono.defer(() -> computeAndPost(event, game, period, fromStr, toStr)));
    }

    private Mono<Void> computeAndPost(ButtonInteractionEvent event,
                                       String game, String period,
                                       String fromStr, String toStr) {
        try {
            LocalDate today = puzzleCalendar.today();
            LocalDate from;
            LocalDate to;

            if (BotText.STATS_PERIOD_CUSTOM.equals(period)) {
                from = LocalDate.parse(fromStr);
                to   = LocalDate.parse(toStr);
            } else {
                LocalDate[] window = resolveWindow(period, today, statsProperties.getAnchorDate());
                from = window[0];
                to   = window[1];
            }

            GameTypeFilter filter = gameFilter(game);
            String label          = periodLabel(period);

            var report  = statsService.compute(filter, from, to);
            String mainContent = reportBuilder.render(report, label);
            List<String> dowBreakdowns = reportBuilder.renderDowBreakdowns(report);
            String statsChannelId = channelProperties.getStatsChannelId();

            Mono<Void> postAll = statsChannelService.post(mainContent);
            for (String dow : dowBreakdowns) {
                postAll = postAll.then(statsChannelService.post(dow));
            }
            return postAll
                    .then(event.editReply(String.format(BotText.STATS_POSTED, statsChannelId)))
                    .then();
        } catch (Exception e) {
            log.error("Error computing stats report from button confirm", e);
            return event.editReply("⚠️ An error occurred while computing the report.").then();
        }
    }

    public static LocalDate[] resolveWindow(String period, LocalDate today, LocalDate anchor) {
        return switch (period) {
            case "week"     -> new LocalDate[]{ today.minusDays(7), today.minusDays(1) };
            case "month"    -> new LocalDate[]{ today.minusMonths(1).plusDays(1), today.minusDays(1) };
            case "year"     -> new LocalDate[]{ today.minusYears(1).plusDays(1), today.minusDays(1) };
            case "all-time" -> {
                LocalDate floor = (anchor != null) ? anchor : today.minusYears(10);
                yield new LocalDate[]{ floor, today.minusDays(1) };
            }
            default -> new LocalDate[]{ today.minusDays(7), today.minusDays(1) };
        };
    }

    public static GameTypeFilter gameFilter(String game) {
        return switch (game) {
            case "mini" -> GameTypeFilter.MINI;
            case "midi" -> GameTypeFilter.MIDI;
            case "main" -> GameTypeFilter.MAIN;
            default     -> GameTypeFilter.ALL;
        };
    }

    public static String periodLabel(String period) {
        return switch (period) {
            case "week"     -> BotText.STATS_PERIOD_LABEL_WEEKLY;
            case "month"    -> BotText.STATS_PERIOD_LABEL_MONTHLY;
            case "year"     -> BotText.STATS_PERIOD_LABEL_YEARLY;
            case "all-time" -> BotText.STATS_PERIOD_LABEL_ALL_TIME;
            default         -> BotText.STATS_PERIOD_LABEL_CUSTOM;
        };
    }
}

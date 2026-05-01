package com.wandocorp.nytscorebot.discord;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.StatsProperties;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.service.stats.CrosswordStatsReportBuilder;
import com.wandocorp.nytscorebot.service.stats.CrosswordStatsService;
import com.wandocorp.nytscorebot.service.stats.GameTypeFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Posts a weekly "all games" crossword stats report every Sunday at 21:00.
 *
 * <p>Window: {@code today.minusDays(7) .. today.minusDays(1)}.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class WeeklyCrosswordStatsJob {

    private final CrosswordStatsService statsService;
    private final CrosswordStatsReportBuilder reportBuilder;
    private final StatsChannelService statsChannelService;
    private final StatsProperties statsProperties;
    private final DiscordChannelProperties channelProperties;
    private final PuzzleCalendar puzzleCalendar;

    @Scheduled(cron = "0 0 21 * * SUN", zone = "${discord.timezone:Europe/London}")
    public void post() {
        run();
    }

    /** Visible for testing — performs the weekly post synchronously. */
    void run() {
        if (!statsProperties.isEnabled()) {
            log.warn("Weekly stats job skipped: stats.anchor-date is not configured");
            return;
        }
        String statsChannelId = channelProperties.getStatsChannelId();
        if (statsChannelId == null || statsChannelId.isBlank()) {
            log.warn("Weekly stats job skipped: discord.statsChannelId is not configured");
            return;
        }
        try {
            LocalDate today = puzzleCalendar.today();
            LocalDate from = today.minusDays(7);
            LocalDate to = today.minusDays(1);

            var report = statsService.compute(GameTypeFilter.ALL, from, to);
            String content = reportBuilder.render(report, BotText.STATS_PERIOD_LABEL_WEEKLY);
            statsChannelService.post(content).block();
        } catch (Exception e) {
            log.error("Weekly crossword stats job failed", e);
        }
    }
}

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
import java.time.YearMonth;

/**
 * Posts a monthly "all games" crossword stats report on the 1st of each month at 09:00.
 *
 * <p>Window: previous calendar month.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class MonthlyCrosswordStatsJob {

    private final CrosswordStatsService statsService;
    private final CrosswordStatsReportBuilder reportBuilder;
    private final StatsChannelService statsChannelService;
    private final StatsProperties statsProperties;
    private final DiscordChannelProperties channelProperties;
    private final PuzzleCalendar puzzleCalendar;

    @Scheduled(cron = "0 0 9 1 * *", zone = "${discord.timezone:Europe/London}")
    public void post() {
        run();
    }

    /** Visible for testing — performs the monthly post synchronously. */
    void run() {
        if (!statsProperties.isEnabled()) {
            log.warn("Monthly stats job skipped: stats.anchor-date is not configured");
            return;
        }
        String statsChannelId = channelProperties.getStatsChannelId();
        if (statsChannelId == null || statsChannelId.isBlank()) {
            log.warn("Monthly stats job skipped: discord.statsChannelId is not configured");
            return;
        }
        try {
            LocalDate today = puzzleCalendar.today();
            YearMonth prevMonth = YearMonth.from(today).minusMonths(1);
            LocalDate from = prevMonth.atDay(1);
            LocalDate to = prevMonth.atEndOfMonth();

            var report = statsService.compute(GameTypeFilter.ALL, from, to);
            String content = reportBuilder.render(report, BotText.STATS_PERIOD_LABEL_MONTHLY);
            statsChannelService.post(content).block();
        } catch (Exception e) {
            log.error("Monthly crossword stats job failed", e);
        }
    }
}

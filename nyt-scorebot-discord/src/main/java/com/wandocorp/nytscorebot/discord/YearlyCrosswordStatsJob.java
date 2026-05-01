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
import java.time.Year;

/**
 * Posts a yearly "all games" crossword stats report on 1 January at 09:00.
 *
 * <p>Window: previous calendar year.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class YearlyCrosswordStatsJob {

    private final CrosswordStatsService statsService;
    private final CrosswordStatsReportBuilder reportBuilder;
    private final StatsChannelService statsChannelService;
    private final StatsProperties statsProperties;
    private final DiscordChannelProperties channelProperties;
    private final PuzzleCalendar puzzleCalendar;

    @Scheduled(cron = "0 0 9 1 1 *", zone = "${discord.timezone:Europe/London}")
    public void post() {
        run();
    }

    /** Visible for testing — performs the yearly post synchronously. */
    void run() {
        if (!statsProperties.isEnabled()) {
            log.warn("Yearly stats job skipped: stats.anchor-date is not configured");
            return;
        }
        String statsChannelId = channelProperties.getStatsChannelId();
        if (statsChannelId == null || statsChannelId.isBlank()) {
            log.warn("Yearly stats job skipped: discord.statsChannelId is not configured");
            return;
        }
        try {
            LocalDate today = puzzleCalendar.today();
            Year prevYear = Year.from(today).minusYears(1);
            LocalDate from = prevYear.atDay(1);
            LocalDate to = prevYear.atDay(prevYear.length());

            var report = statsService.compute(GameTypeFilter.ALL, from, to);
            String content = reportBuilder.render(report, BotText.STATS_PERIOD_LABEL_YEARLY);
            statsChannelService.post(content).block();
        } catch (Exception e) {
            log.error("Yearly crossword stats job failed", e);
        }
    }
}

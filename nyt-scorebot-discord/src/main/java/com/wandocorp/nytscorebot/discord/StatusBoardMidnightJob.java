package com.wandocorp.nytscorebot.discord;

import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Resets the persistent status board to its empty (all-pending) state at midnight
 * in the puzzle timezone, so the board reflects a fresh day before any submissions arrive.
 * Also force-posts yesterday's results boards if they were not already posted
 * (i.e. neither player reached the "both finished" trigger during the day).
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class StatusBoardMidnightJob {

    private final StatusChannelService statusChannelService;
    private final ResultsChannelService resultsChannelService;
    private final PuzzleCalendar puzzleCalendar;

    /**
     * Runs at 00:00 in the configured puzzle timezone (defaulting to Europe/London).
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "${discord.timezone:Europe/London}")
    public void resetStatusBoard() {
        run();
    }

    /** Visible for testing — performs the job synchronously. */
    void run() {
        try {
            forcePostYesterdayIfNeeded();
        } catch (Exception e) {
            log.error("Failed to force-post yesterday's results at midnight", e);
        }
        try {
            statusChannelService.resetForNewDay();
        } catch (Exception e) {
            log.error("Failed to reset status board at midnight", e);
        }
    }

    private void forcePostYesterdayIfNeeded() {
        LocalDate yesterday = puzzleCalendar.today().minusDays(1);
        if (!resultsChannelService.hasPostedResultsForDate(yesterday)) {
            log.info("Force-posting results for {} at midnight (results not yet posted)", yesterday);
            resultsChannelService.forceRefreshForDate(yesterday);
        }
    }
}

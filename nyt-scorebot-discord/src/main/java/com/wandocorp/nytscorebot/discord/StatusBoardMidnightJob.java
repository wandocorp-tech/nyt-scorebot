package com.wandocorp.nytscorebot.discord;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Publishes the closing day's results boards when they were never posted during the day
 * (i.e. neither player reached the "both finished" trigger), and re-evaluates the triple
 * crown for that date.
 *
 * <p>Not scheduled directly — {@link MidnightRolloverJob} invokes these steps in a defined
 * order, because they must run after win-streak forfeits have been applied.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class StatusBoardMidnightJob {

    private final ResultsChannelService resultsChannelService;

    /**
     * Force-posts the given date's boards unless they have already been published.
     *
     * <p>The already-posted guard exists because slot tracking is in-memory: after a restart
     * the bot no longer knows which messages it posted, and an unguarded refresh would post
     * duplicates rather than editing in place.
     */
    public void forcePostIfNeeded(LocalDate date) {
        if (resultsChannelService.hasPostedResultsForDate(date)) {
            log.info("Skipping midnight force-post for {} — results already posted", date);
            return;
        }
        log.info("Force-posting results for {} at midnight (results not yet posted)", date);
        resultsChannelService.forceRefreshForDate(date);
    }

    /**
     * Re-evaluates the triple crown for the given date. Runs even when
     * {@link #forcePostIfNeeded} was skipped, so a sweep that only completes once forfeits
     * are applied is still recognised.
     */
    public void evaluateTripleCrown(LocalDate date) {
        resultsChannelService.refreshTripleCrownForDate(date);
    }
}

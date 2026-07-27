package com.wandocorp.nytscorebot.discord;

import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * Single ordered entry point for everything that must happen at 00:00 in the puzzle timezone.
 *
 * <p>The steps were previously two independently scheduled jobs sharing one cron expression.
 * Spring serialises them on the default single-threaded scheduler but their relative order is
 * an accident of bean registration, which matters because the steps are not commutative:
 *
 * <ol>
 *   <li>Apply crossword win-streak forfeits for the closing date. A non-submission only becomes
 *       a forfeit <em>win</em> here.</li>
 *   <li>Force-post the closing date's boards, so a day where neither player triggered the
 *       "both finished" path still publishes. Must follow step 1 so the rendered win-streak
 *       summary shows finalized values.</li>
 *   <li>Evaluate the triple crown for the closing date. Must follow step 1 so a sweep achieved
 *       by forfeit is recognised, and follow step 2 so the celebration lands last.</li>
 *   <li>Reset the status board for the new day.</li>
 * </ol>
 *
 * <p>Each step is independently guarded so a failure in one cannot suppress the rest.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class MidnightRolloverJob {

    private final WinStreakMidnightJob winStreakMidnightJob;
    private final StatusBoardMidnightJob statusBoardMidnightJob;
    private final StatusChannelService statusChannelService;
    private final PuzzleCalendar puzzleCalendar;

    /**
     * Runs at 00:00 in the configured puzzle timezone (defaulting to Europe/London).
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "${discord.timezone:Europe/London}")
    public void rollover() {
        run();
    }

    /** Visible for testing — performs the rollover synchronously. */
    void run() {
        LocalDate closingDate = puzzleCalendar.today().minusDays(1);
        log.info("Midnight rollover starting for closing date {}", closingDate);

        step("apply win streak forfeits", d -> winStreakMidnightJob.applyForfeitsFor(d), closingDate);
        step("force-post results", d -> statusBoardMidnightJob.forcePostIfNeeded(d), closingDate);
        step("evaluate triple crown", d -> statusBoardMidnightJob.evaluateTripleCrown(d), closingDate);
        step("reset status board", d -> statusChannelService.resetForNewDay(), closingDate);

        log.info("Midnight rollover complete for closing date {}", closingDate);
    }

    private void step(String description, Consumer<LocalDate> action, LocalDate closingDate) {
        try {
            action.accept(closingDate);
        } catch (Exception e) {
            log.error("Midnight rollover step '{}' failed for {}", description, closingDate, e);
        }
    }
}

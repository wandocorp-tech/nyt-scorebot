package com.wandocorp.nytscorebot.discord;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Resets the persistent status board to its empty (all-pending) state at midnight
 * in the puzzle timezone, so the board reflects a fresh day before any submissions arrive.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class StatusBoardMidnightJob {

    private final StatusChannelService statusChannelService;

    /**
     * Runs at 00:00 in the configured puzzle timezone (defaulting to Europe/London).
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "${discord.timezone:Europe/London}")
    public void resetStatusBoard() {
        run();
    }

    /** Visible for testing — performs the reset synchronously. */
    void run() {
        try {
            statusChannelService.resetForNewDay();
        } catch (Exception e) {
            log.error("Failed to reset status board at midnight", e);
        }
    }
}

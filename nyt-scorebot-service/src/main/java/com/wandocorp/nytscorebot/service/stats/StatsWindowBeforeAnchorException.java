package com.wandocorp.nytscorebot.service.stats;

import java.time.LocalDate;

/** Thrown when the requested stats window is entirely before the configured anchor date. */
public class StatsWindowBeforeAnchorException extends RuntimeException {

    private final LocalDate anchor;

    public StatsWindowBeforeAnchorException(LocalDate anchor) {
        super("Requested window is entirely before the anchor date " + anchor);
        this.anchor = anchor;
    }

    public LocalDate getAnchor() {
        return anchor;
    }
}

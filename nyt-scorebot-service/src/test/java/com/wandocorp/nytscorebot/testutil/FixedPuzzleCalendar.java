package com.wandocorp.nytscorebot.testutil;

import com.wandocorp.nytscorebot.service.PuzzleCalendar;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Test stub that pins {@link PuzzleCalendar#today()} to a fixed date
 * so tests are deterministic regardless of the real clock.
 */
public class FixedPuzzleCalendar extends PuzzleCalendar {

    private final LocalDate fixedDate;

    public FixedPuzzleCalendar(LocalDate fixedDate) {
        super(ZoneId.of("Europe/London"));
        this.fixedDate = fixedDate;
    }

    @Override
    public LocalDate today() {
        return fixedDate;
    }
}

package com.wandocorp.nytscorebot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Calculates the expected puzzle number for each NYT game on a given day.
 * Dates are resolved in the configured timezone (default Europe/London) so that
 * the submission window is a full calendar day, midnight to midnight.
 */
@Service
public class PuzzleCalendar {

    private final ZoneId zone;

    // Wordle puzzle #0 was published on 2021-06-19
    static final LocalDate WORDLE_EPOCH = LocalDate.of(2021, 6, 19);

    // Connections puzzle #1 was published on 2023-06-12
    static final LocalDate CONNECTIONS_EPOCH = LocalDate.of(2023, 6, 12);

    // Strands puzzle #1 was published on 2024-03-04
    static final LocalDate STRANDS_EPOCH = LocalDate.of(2024, 3, 4);

    @Autowired
    public PuzzleCalendar(@Value("${discord.timezone:Europe/London}") String timezone) {
        this(ZoneId.of(timezone));
    }

    protected PuzzleCalendar(ZoneId zone) {
        this.zone = zone;
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    public int expectedWordle() {
        return expectedWordle(today());
    }

    public int expectedConnections() {
        return expectedConnections(today());
    }

    public int expectedStrands() {
        return expectedStrands(today());
    }

    /**
     * Wordle #0 = 2021-06-19, so puzzle number = days since epoch.
     */
    int expectedWordle(LocalDate date) {
        return (int) ChronoUnit.DAYS.between(WORDLE_EPOCH, date);
    }

    /**
     * Connections #1 = 2023-06-12, so puzzle number = 1 + days since epoch.
     */
    int expectedConnections(LocalDate date) {
        return 1 + (int) ChronoUnit.DAYS.between(CONNECTIONS_EPOCH, date);
    }

    /**
     * Strands #1 = 2024-03-04, so puzzle number = 1 + days since epoch.
     */
    int expectedStrands(LocalDate date) {
        return 1 + (int) ChronoUnit.DAYS.between(STRANDS_EPOCH, date);
    }
}

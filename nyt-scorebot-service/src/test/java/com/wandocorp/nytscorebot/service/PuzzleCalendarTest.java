package com.wandocorp.nytscorebot.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PuzzleCalendar} anchor-date calculations.
 * Uses known puzzle numbers on specific dates to verify the formulas.
 */
class PuzzleCalendarTest {

    private final PuzzleCalendar calendar = new PuzzleCalendar("Europe/London");

    @Test
    void wordleEpochReturnsPuzzleZero() {
        assertThat(calendar.expectedWordle(LocalDate.of(2021, 6, 19))).isEqualTo(0);
    }

    @Test
    void wordleDayAfterEpochReturnsPuzzleOne() {
        assertThat(calendar.expectedWordle(LocalDate.of(2021, 6, 20))).isEqualTo(1);
    }

    @Test
    void wordleKnownPuzzle() {
        // Wordle 1,000 should be 1000 days after June 19, 2021 = March 15, 2024
        assertThat(calendar.expectedWordle(LocalDate.of(2024, 3, 15))).isEqualTo(1000);
    }

    @Test
    void connectionsEpochReturnsPuzzleOne() {
        assertThat(calendar.expectedConnections(LocalDate.of(2023, 6, 12))).isEqualTo(1);
    }

    @Test
    void connectionsDayAfterEpochReturnsPuzzleTwo() {
        assertThat(calendar.expectedConnections(LocalDate.of(2023, 6, 13))).isEqualTo(2);
    }

    @Test
    void strandsEpochReturnsPuzzleOne() {
        assertThat(calendar.expectedStrands(LocalDate.of(2024, 3, 4))).isEqualTo(1);
    }

    @Test
    void strandsDayAfterEpochReturnsPuzzleTwo() {
        assertThat(calendar.expectedStrands(LocalDate.of(2024, 3, 5))).isEqualTo(2);
    }

    @Test
    void todayReturnsNonNull() {
        assertThat(calendar.today()).isNotNull();
    }
}

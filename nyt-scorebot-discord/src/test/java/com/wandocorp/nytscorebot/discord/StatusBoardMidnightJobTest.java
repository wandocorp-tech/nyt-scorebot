package com.wandocorp.nytscorebot.discord;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatusBoardMidnightJobTest {

    private static final LocalDate CLOSING_DATE = LocalDate.of(2026, 5, 3);

    private ResultsChannelService resultsService;
    private StatusBoardMidnightJob job;

    @BeforeEach
    void setUp() {
        resultsService = mock(ResultsChannelService.class);
        job = new StatusBoardMidnightJob(resultsService);
    }

    @Test
    void forcePostsWhenTheDateWasNeverPosted() {
        when(resultsService.hasPostedResultsForDate(CLOSING_DATE)).thenReturn(false);

        job.forcePostIfNeeded(CLOSING_DATE);

        verify(resultsService).forceRefreshForDate(CLOSING_DATE);
    }

    @Test
    void skipsForcePostWhenTheDateWasAlreadyPosted() {
        when(resultsService.hasPostedResultsForDate(CLOSING_DATE)).thenReturn(true);

        job.forcePostIfNeeded(CLOSING_DATE);

        verify(resultsService, never()).forceRefreshForDate(CLOSING_DATE);
    }

    @Test
    void evaluatesTheTripleCrownForTheGivenDate() {
        job.evaluateTripleCrown(CLOSING_DATE);

        verify(resultsService).refreshTripleCrownForDate(CLOSING_DATE);
    }

    @Test
    void tripleCrownIsEvaluatedIndependentlyOfThePostedGuard() {
        when(resultsService.hasPostedResultsForDate(CLOSING_DATE)).thenReturn(true);

        job.forcePostIfNeeded(CLOSING_DATE);
        job.evaluateTripleCrown(CLOSING_DATE);

        // A sweep that only completes once forfeits are applied must still be recognised,
        // even on a day whose boards were already published during the day.
        verify(resultsService, never()).forceRefreshForDate(CLOSING_DATE);
        verify(resultsService).refreshTripleCrownForDate(CLOSING_DATE);
    }
}

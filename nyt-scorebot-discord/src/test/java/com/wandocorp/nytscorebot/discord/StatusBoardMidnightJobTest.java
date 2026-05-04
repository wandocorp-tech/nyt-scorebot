package com.wandocorp.nytscorebot.discord;

import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatusBoardMidnightJobTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 4);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);

    private StatusBoardMidnightJob job(StatusChannelService statusService,
                                       ResultsChannelService resultsService,
                                       PuzzleCalendar calendar) {
        return new StatusBoardMidnightJob(statusService, resultsService, calendar);
    }

    private PuzzleCalendar calendarReturning(LocalDate today) {
        PuzzleCalendar cal = mock(PuzzleCalendar.class);
        when(cal.today()).thenReturn(today);
        return cal;
    }

    @Test
    void runDelegatesToStatusChannelService() {
        StatusChannelService statusService = mock(StatusChannelService.class);
        ResultsChannelService resultsService = mock(ResultsChannelService.class);
        when(resultsService.hasPostedResultsForDate(YESTERDAY)).thenReturn(true);

        job(statusService, resultsService, calendarReturning(TODAY)).run();

        verify(statusService).resetForNewDay();
    }

    @Test
    void runForcePostsYesterdayResultsWhenNotYetPosted() {
        StatusChannelService statusService = mock(StatusChannelService.class);
        ResultsChannelService resultsService = mock(ResultsChannelService.class);
        when(resultsService.hasPostedResultsForDate(YESTERDAY)).thenReturn(false);

        job(statusService, resultsService, calendarReturning(TODAY)).run();

        verify(resultsService).forceRefreshForDate(YESTERDAY);
    }

    @Test
    void runSkipsForcePostWhenYesterdayAlreadyPosted() {
        StatusChannelService statusService = mock(StatusChannelService.class);
        ResultsChannelService resultsService = mock(ResultsChannelService.class);
        when(resultsService.hasPostedResultsForDate(YESTERDAY)).thenReturn(true);

        job(statusService, resultsService, calendarReturning(TODAY)).run();

        verify(resultsService, never()).forceRefreshForDate(YESTERDAY);
    }

    @Test
    void runSwallowsExceptionsFromStatusChannelService() {
        StatusChannelService statusService = mock(StatusChannelService.class);
        ResultsChannelService resultsService = mock(ResultsChannelService.class);
        when(resultsService.hasPostedResultsForDate(YESTERDAY)).thenReturn(true);
        doThrow(new RuntimeException("boom")).when(statusService).resetForNewDay();

        // Must not throw — failure is logged and swallowed
        job(statusService, resultsService, calendarReturning(TODAY)).run();

        verify(statusService).resetForNewDay();
    }

    @Test
    void runSwallowsExceptionsFromResultsChannelService() {
        StatusChannelService statusService = mock(StatusChannelService.class);
        ResultsChannelService resultsService = mock(ResultsChannelService.class);
        when(resultsService.hasPostedResultsForDate(YESTERDAY)).thenReturn(false);
        doThrow(new RuntimeException("boom")).when(resultsService).forceRefreshForDate(YESTERDAY);

        // Must not throw — failure is logged and swallowed, and reset still runs
        job(statusService, resultsService, calendarReturning(TODAY)).run();

        verify(statusService).resetForNewDay();
    }
}

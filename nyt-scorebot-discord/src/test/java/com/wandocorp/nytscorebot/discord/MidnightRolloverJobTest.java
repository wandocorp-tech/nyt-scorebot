package com.wandocorp.nytscorebot.discord;

import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDate;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MidnightRolloverJobTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 5, 4);
    private static final LocalDate CLOSING_DATE = TODAY.minusDays(1);

    private WinStreakMidnightJob winStreakJob;
    private StatusBoardMidnightJob statusBoardJob;
    private StatusChannelService statusChannelService;
    private MidnightRolloverJob job;

    @BeforeEach
    void setUp() {
        winStreakJob = mock(WinStreakMidnightJob.class);
        statusBoardJob = mock(StatusBoardMidnightJob.class);
        statusChannelService = mock(StatusChannelService.class);
        PuzzleCalendar calendar = mock(PuzzleCalendar.class);
        when(calendar.today()).thenReturn(TODAY);
        job = new MidnightRolloverJob(winStreakJob, statusBoardJob, statusChannelService, calendar);
    }

    @Test
    void stepsRunInTheOrderTheyDependOn() {
        job.run();

        InOrder order = inOrder(winStreakJob, statusBoardJob, statusChannelService);
        // Forfeits first: a non-submission only becomes a forfeit win here, and both the
        // rendered summary and the crown must see finalized values.
        order.verify(winStreakJob).applyForfeitsFor(CLOSING_DATE);
        order.verify(statusBoardJob).forcePostIfNeeded(CLOSING_DATE);
        // Crown last of the closing-day steps so the celebration lands beneath the boards.
        order.verify(statusBoardJob).evaluateTripleCrown(CLOSING_DATE);
        order.verify(statusChannelService).resetForNewDay();
        order.verifyNoMoreInteractions();
    }

    @Test
    void everyStepOperatesOnTheClosingDateNotTheNewDay() {
        job.run();

        verify(winStreakJob).applyForfeitsFor(CLOSING_DATE);
        verify(statusBoardJob).forcePostIfNeeded(CLOSING_DATE);
        verify(statusBoardJob).evaluateTripleCrown(CLOSING_DATE);
    }

    @Test
    void scheduledEntryPointPerformsTheRollover() {
        job.rollover();

        verify(winStreakJob).applyForfeitsFor(CLOSING_DATE);
        verify(statusChannelService).resetForNewDay();
    }

    @Test
    void aFailingForfeitStepDoesNotSuppressLaterSteps() {
        doThrow(new RuntimeException("boom")).when(winStreakJob).applyForfeitsFor(CLOSING_DATE);

        job.run();

        verify(statusBoardJob).forcePostIfNeeded(CLOSING_DATE);
        verify(statusBoardJob).evaluateTripleCrown(CLOSING_DATE);
        verify(statusChannelService).resetForNewDay();
    }

    @Test
    void aFailingForcePostStepDoesNotSuppressLaterSteps() {
        doThrow(new RuntimeException("boom")).when(statusBoardJob).forcePostIfNeeded(CLOSING_DATE);

        job.run();

        verify(statusBoardJob).evaluateTripleCrown(CLOSING_DATE);
        verify(statusChannelService).resetForNewDay();
    }

    @Test
    void aFailingCrownStepDoesNotSuppressTheStatusReset() {
        doThrow(new RuntimeException("boom")).when(statusBoardJob).evaluateTripleCrown(CLOSING_DATE);

        job.run();

        verify(statusChannelService).resetForNewDay();
    }

    @Test
    void aFailingStatusResetDoesNotPropagate() {
        doThrow(new RuntimeException("boom")).when(statusChannelService).resetForNewDay();

        // Must not throw — the scheduler would otherwise log an unhandled error.
        job.run();

        verify(statusChannelService).resetForNewDay();
    }
}

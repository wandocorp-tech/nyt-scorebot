package com.wandocorp.nytscorebot.discord;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StatusBoardMidnightJobTest {

    @Test
    void runDelegatesToStatusChannelService() {
        StatusChannelService statusService = mock(StatusChannelService.class);
        StatusBoardMidnightJob job = new StatusBoardMidnightJob(statusService);

        job.run();

        verify(statusService).resetForNewDay();
    }

    @Test
    void runSwallowsExceptionsFromStatusChannelService() {
        StatusChannelService statusService = mock(StatusChannelService.class);
        doThrow(new RuntimeException("boom")).when(statusService).resetForNewDay();
        StatusBoardMidnightJob job = new StatusBoardMidnightJob(statusService);

        // Must not throw — failure is logged and swallowed
        job.run();

        verify(statusService).resetForNewDay();
    }
}

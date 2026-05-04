package com.wandocorp.nytscorebot.discord;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.StatsProperties;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.service.stats.CrosswordStatsReport;
import com.wandocorp.nytscorebot.service.stats.CrosswordStatsReportBuilder;
import com.wandocorp.nytscorebot.service.stats.CrosswordStatsService;
import com.wandocorp.nytscorebot.service.stats.GameTypeFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class YearlyCrosswordStatsJobTest {

    // Run on 1 Jan 2026
    private static final LocalDate TODAY = LocalDate.of(2026, 1, 1);

    private CrosswordStatsService statsService;
    private CrosswordStatsReportBuilder reportBuilder;
    private StatsChannelService statsChannelService;
    private StatsProperties statsProperties;
    private DiscordChannelProperties channelProperties;
    private PuzzleCalendar puzzleCalendar;
    private YearlyCrosswordStatsJob job;

    @BeforeEach
    void setUp() {
        statsService        = mock(CrosswordStatsService.class);
        reportBuilder       = mock(CrosswordStatsReportBuilder.class);
        statsChannelService = mock(StatsChannelService.class);
        statsProperties     = mock(StatsProperties.class);
        channelProperties   = new DiscordChannelProperties();
        puzzleCalendar      = mock(PuzzleCalendar.class);

        channelProperties.setStatsChannelId("stats-ch");
        when(statsProperties.isEnabled()).thenReturn(true);
        when(puzzleCalendar.today()).thenReturn(TODAY);

        CrosswordStatsReport fakeReport = mock(CrosswordStatsReport.class);
        when(fakeReport.games()).thenReturn(List.of());
        when(statsService.compute(any(), any(), any())).thenReturn(fakeReport);
        when(reportBuilder.render(any(), any())).thenReturn("yearly report");
        when(reportBuilder.renderDowBreakdowns(any())).thenReturn(List.of());
        when(statsChannelService.post(any())).thenReturn(Mono.empty());

        job = new YearlyCrosswordStatsJob(statsService, reportBuilder, statsChannelService,
                statsProperties, channelProperties, puzzleCalendar);
    }

    @Test
    void postsForPreviousCalendarYear() {
        job.run();

        verify(statsService).compute(eq(GameTypeFilter.ALL),
                eq(LocalDate.of(2025, 1, 1)), eq(LocalDate.of(2025, 12, 31)));
        verify(reportBuilder).render(any(), eq(BotText.STATS_PERIOD_LABEL_YEARLY));
        verify(statsChannelService).post("yearly report");
    }

    @Test
    void noOpWhenAnchorUnset() {
        when(statsProperties.isEnabled()).thenReturn(false);
        job.run();
        verify(statsService, never()).compute(any(), any(), any());
    }

    @Test
    void noOpWhenStatsChannelUnset() {
        channelProperties.setStatsChannelId(null);
        job.run();
        verify(statsService, never()).compute(any(), any(), any());
    }

    @Test
    void swallowsException() {
        when(statsService.compute(any(), any(), any())).thenThrow(new RuntimeException("boom"));
        job.run(); // must not throw
    }
}

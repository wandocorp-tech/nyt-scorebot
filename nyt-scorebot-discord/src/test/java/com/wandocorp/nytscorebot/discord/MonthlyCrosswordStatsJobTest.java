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
import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonthlyCrosswordStatsJobTest {

    // Run on 1st of March 2026
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 1);

    private CrosswordStatsService statsService;
    private CrosswordStatsReportBuilder reportBuilder;
    private StatsChannelService statsChannelService;
    private StatsProperties statsProperties;
    private DiscordChannelProperties channelProperties;
    private PuzzleCalendar puzzleCalendar;
    private MonthlyCrosswordStatsJob job;

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
        when(reportBuilder.render(any(), any())).thenReturn("report");
        when(statsChannelService.post(any())).thenReturn(Mono.empty());

        job = new MonthlyCrosswordStatsJob(statsService, reportBuilder, statsChannelService,
                statsProperties, channelProperties, puzzleCalendar);
    }

    @Test
    void postsForPreviousCalendarMonth() {
        job.run();

        YearMonth prevMonth = YearMonth.of(2026, 2);
        verify(statsService).compute(eq(GameTypeFilter.ALL),
                eq(prevMonth.atDay(1)), eq(prevMonth.atEndOfMonth()));
        verify(reportBuilder).render(any(), eq(BotText.STATS_PERIOD_LABEL_MONTHLY));
        verify(statsChannelService).post("report");
    }

    @Test
    void noOpWhenAnchorUnset() {
        when(statsProperties.isEnabled()).thenReturn(false);
        job.run();
        verify(statsService, never()).compute(any(), any(), any());
    }

    @Test
    void noOpWhenStatsChannelUnset() {
        channelProperties.setStatsChannelId("");
        job.run();
        verify(statsService, never()).compute(any(), any(), any());
    }

    @Test
    void swallowsException() {
        when(statsService.compute(any(), any(), any())).thenThrow(new RuntimeException("error"));
        job.run(); // must not throw
    }
}

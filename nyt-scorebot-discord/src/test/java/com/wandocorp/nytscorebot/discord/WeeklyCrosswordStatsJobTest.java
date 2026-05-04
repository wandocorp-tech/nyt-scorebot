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

class WeeklyCrosswordStatsJobTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 4, 13); // Sunday

    private CrosswordStatsService statsService;
    private CrosswordStatsReportBuilder reportBuilder;
    private StatsChannelService statsChannelService;
    private StatsProperties statsProperties;
    private DiscordChannelProperties channelProperties;
    private PuzzleCalendar puzzleCalendar;
    private WeeklyCrosswordStatsJob job;

    @BeforeEach
    void setUp() {
        statsService       = mock(CrosswordStatsService.class);
        reportBuilder      = mock(CrosswordStatsReportBuilder.class);
        statsChannelService = mock(StatsChannelService.class);
        statsProperties    = mock(StatsProperties.class);
        channelProperties  = new DiscordChannelProperties();
        puzzleCalendar     = mock(PuzzleCalendar.class);

        channelProperties.setStatsChannelId("stats-ch");
        when(statsProperties.isEnabled()).thenReturn(true);
        when(statsProperties.getAnchorDate()).thenReturn(null);
        when(puzzleCalendar.today()).thenReturn(TODAY);

        CrosswordStatsReport fakeReport = mock(CrosswordStatsReport.class);
        when(fakeReport.games()).thenReturn(List.of());
        when(statsService.compute(any(), any(), any())).thenReturn(fakeReport);
        when(reportBuilder.render(any(), any())).thenReturn("report content");
        when(reportBuilder.renderDowBreakdowns(any())).thenReturn(List.of());
        when(statsChannelService.post(any())).thenReturn(Mono.empty());

        job = new WeeklyCrosswordStatsJob(statsService, reportBuilder, statsChannelService,
                statsProperties, channelProperties, puzzleCalendar);
    }

    @Test
    void postsWeeklyReportToStatsChannel() {
        job.run();

        LocalDate expectedFrom = TODAY.minusDays(7);
        LocalDate expectedTo   = TODAY.minusDays(1);
        verify(statsService).compute(eq(GameTypeFilter.ALL), eq(expectedFrom), eq(expectedTo));
        verify(reportBuilder).render(any(), eq(BotText.STATS_PERIOD_LABEL_WEEKLY));
        verify(statsChannelService).post("report content");
    }

    @Test
    void noOpWhenAnchorUnset() {
        when(statsProperties.isEnabled()).thenReturn(false);
        job.run();
        verify(statsService, never()).compute(any(), any(), any());
        verify(statsChannelService, never()).post(any());
    }

    @Test
    void noOpWhenStatsChannelUnset() {
        channelProperties.setStatsChannelId(null);
        job.run();
        verify(statsService, never()).compute(any(), any(), any());
        verify(statsChannelService, never()).post(any());
    }

    @Test
    void swallowsExceptionWithoutRethrow() {
        when(statsService.compute(any(), any(), any())).thenThrow(new RuntimeException("db error"));
        // Should not throw
        job.run();
    }
}

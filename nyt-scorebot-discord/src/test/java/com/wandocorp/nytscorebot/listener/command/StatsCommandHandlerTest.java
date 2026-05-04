package com.wandocorp.nytscorebot.listener.command;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.StatsProperties;
import com.wandocorp.nytscorebot.discord.StatsChannelService;
import com.wandocorp.nytscorebot.listener.StatsConfirmationTracker;
import com.wandocorp.nytscorebot.repository.ScoreboardRepository;
import com.wandocorp.nytscorebot.service.PuzzleCalendar;
import com.wandocorp.nytscorebot.service.stats.CrosswordStatsReport;
import com.wandocorp.nytscorebot.service.stats.CrosswordStatsReportBuilder;
import com.wandocorp.nytscorebot.service.stats.CrosswordStatsService;
import com.wandocorp.nytscorebot.service.stats.GameTypeFilter;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.spec.InteractionApplicationCommandCallbackReplyMono;
import discord4j.core.spec.InteractionFollowupCreateMono;
import discord4j.core.spec.InteractionReplyEditMono;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Schedulers;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatsCommandHandlerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 4, 15);

    private CrosswordStatsService statsService;
    private CrosswordStatsReportBuilder reportBuilder;
    private StatsChannelService statsChannelService;
    private DiscordChannelProperties channelProperties;
    private StatsProperties statsProperties;
    private StatsConfirmationTracker tracker;
    private PuzzleCalendar puzzleCalendar;
    private ScoreboardRepository scoreboardRepository;
    private StatsCommandHandler handler;

    // Shared reply-chain mocks
    private InteractionApplicationCommandCallbackReplyMono replyMono;
    private InteractionApplicationCommandCallbackReplyMono withEphemeral;
    private InteractionApplicationCommandCallbackReplyMono withContent;

    @BeforeEach
    void setUp() {
        statsService        = mock(CrosswordStatsService.class);
        reportBuilder       = mock(CrosswordStatsReportBuilder.class);
        statsChannelService = mock(StatsChannelService.class);
        channelProperties   = new DiscordChannelProperties();
        statsProperties     = mock(StatsProperties.class);
        tracker             = mock(StatsConfirmationTracker.class);
        puzzleCalendar      = mock(PuzzleCalendar.class);
        scoreboardRepository = mock(ScoreboardRepository.class);

        channelProperties.setStatsChannelId("stats-ch");
        when(statsProperties.isEnabled()).thenReturn(true);
        when(puzzleCalendar.today()).thenReturn(TODAY);

        CrosswordStatsReport fakeReport = mock(CrosswordStatsReport.class);
        when(fakeReport.games()).thenReturn(List.of());
        when(statsService.compute(any(), any(), any())).thenReturn(fakeReport);
        when(reportBuilder.render(any(), any())).thenReturn("rendered report");
        when(reportBuilder.renderDowBreakdowns(any())).thenReturn(List.of());
        when(statsChannelService.post(any())).thenReturn(Mono.empty());

        // VirtualTime scheduler so Mono.delay(15s) never fires in tests
        handler = new StatsCommandHandler(statsService, reportBuilder, statsChannelService,
                channelProperties, statsProperties, tracker, puzzleCalendar,
                scoreboardRepository, Schedulers.immediate());
    }

    @Test
    void commandNameIsStats() {
        assertThat(handler.commandName()).isEqualTo(BotText.CMD_STATS);
    }

    @Test
    void rejectsWhenAnchorUnset() {
        when(statsProperties.isEnabled()).thenReturn(false);
        ChatInputInteractionEvent event = buildEvent("all", "week", null, null);

        handler.handle(event).subscribe();

        verify(withEphemeral).withContent(BotText.STATS_ERR_ANCHOR_UNSET);
        verify(statsService, never()).compute(any(), any(), any());
    }

    @Test
    void rejectsWhenStatsChannelUnset() {
        channelProperties.setStatsChannelId(null);
        ChatInputInteractionEvent event = buildEvent("all", "week", null, null);

        handler.handle(event).subscribe();

        verify(withEphemeral).withContent(BotText.STATS_ERR_CHANNEL_UNSET);
        verify(statsService, never()).compute(any(), any(), any());
    }

    @Test
    void rejectsCustomPeriodWithMissingDates() {
        ChatInputInteractionEvent event = buildEvent("all", "custom", null, null);

        handler.handle(event).subscribe();

        verify(withEphemeral).withContent(BotText.STATS_ERR_CUSTOM_MISSING_DATES);
        verify(statsService, never()).compute(any(), any(), any());
    }

    @Test
    void rejectsNonCustomPeriodWithExtraDates() {
        ChatInputInteractionEvent event = buildEvent("all", "week", "2025-01-01", "2025-01-07");

        handler.handle(event).subscribe();

        verify(withEphemeral).withContent(BotText.STATS_ERR_DATES_ON_NON_CUSTOM);
        verify(statsService, never()).compute(any(), any(), any());
    }

    @Test
    void rejectsInvalidDateFormat() {
        ChatInputInteractionEvent event = buildEvent("all", "custom", "bad-date", "2025-01-07");

        handler.handle(event).subscribe();

        verify(withEphemeral).withContent(BotText.STATS_ERR_INVALID_DATE);
        verify(statsService, never()).compute(any(), any(), any());
    }

    @Test
    void rejectsFromAfterTo() {
        ChatInputInteractionEvent event = buildEvent("all", "custom", "2025-01-07", "2025-01-01");

        handler.handle(event).subscribe();

        verify(withEphemeral).withContent(BotText.STATS_ERR_FROM_AFTER_TO);
        verify(statsService, never()).compute(any(), any(), any());
    }

    @Test
    void rejectsToInFuture() {
        // to = today (not yesterday)
        String futureDate = TODAY.toString();
        ChatInputInteractionEvent event = buildEvent("all", "custom", "2025-01-01", futureDate);

        handler.handle(event).subscribe();

        verify(withEphemeral).withContent(BotText.STATS_ERR_TO_IN_FUTURE);
        verify(statsService, never()).compute(any(), any(), any());
    }

    @Test
    void smallPeriodComputesAndPosts() {
        ChatInputInteractionEvent event = buildEventForSmallPeriod("mini", "week");

        handler.handle(event).subscribe();

        verify(statsService).compute(any(), any(), any());
        verify(statsChannelService).post(any());
    }

    @Test
    void largePeriodShowsConfirmationPrompt() {
        ChatInputInteractionEvent event = buildEvent("all", "year", null, null);

        handler.handle(event).subscribe();

        verify(statsService, never()).compute(any(), any(), any());
        // The reply should contain the confirmation components
        verify(withEphemeral).withContent(BotText.STATS_CONFIRM_PROMPT);
    }

    @Test
    void isLargePeriodYearAndAllTime() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to   = LocalDate.of(2025, 12, 31);
        assertThat(StatsCommandHandler.isLargePeriod("year", from, to)).isTrue();
        assertThat(StatsCommandHandler.isLargePeriod("all-time", from, to)).isTrue();
    }

    @Test
    void isLargePeriodCustomOver90Days() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to   = from.plusDays(91);
        assertThat(StatsCommandHandler.isLargePeriod("custom", from, to)).isTrue();
    }

    @Test
    void isNotLargePeriodCustomUnder90Days() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to   = from.plusDays(30);
        assertThat(StatsCommandHandler.isLargePeriod("custom", from, to)).isFalse();
    }

    @Test
    void rejectsCustomPeriodBeforeAnchor() {
        when(statsProperties.getAnchorDate()).thenReturn(LocalDate.of(2025, 6, 1));
        ChatInputInteractionEvent event = buildEvent("all", "custom", "2025-01-01", "2025-01-07");

        handler.handle(event).subscribe();

        verify(withEphemeral).withContent(contains("2025-06-01"));
        verify(statsService, never()).compute(any(), any(), any());
    }

    @Test
    void smallPeriodAnchorErrorReplaysGracefully() {
        when(statsService.compute(any(), any(), any()))
                .thenThrow(new com.wandocorp.nytscorebot.service.stats.StatsWindowBeforeAnchorException(
                        LocalDate.of(2025, 1, 1)));
        ChatInputInteractionEvent event = buildEventForSmallPeriod("mini", "week");

        handler.handle(event).subscribe();

        verify(statsChannelService, never()).post(any());
    }

    @Test
    void smallPeriodGenericErrorReplaysGracefully() {
        when(statsService.compute(any(), any(), any())).thenThrow(new RuntimeException("boom"));
        ChatInputInteractionEvent event = buildEventForSmallPeriod("mini", "week");

        handler.handle(event).subscribe();

        verify(statsChannelService, never()).post(any());
    }

    @Test
    void largePeriodSchedulesAutoCancelTimer() {
        ChatInputInteractionEvent event = buildEvent("all", "year", null, null);

        handler.handle(event).subscribe();

        verify(tracker).register(contains("all:year"), any());
    }

    @Test
    void defaultConstructorUsesParallelScheduler() {
        StatsCommandHandler h = new StatsCommandHandler(statsService, reportBuilder,
                statsChannelService, channelProperties, statsProperties, tracker,
                puzzleCalendar, scoreboardRepository);
        assertThat(h.commandName()).isEqualTo(BotText.CMD_STATS);
    }

    // ── Event builders ────────────────────────────────────────────────────────

    private ChatInputInteractionEvent buildEvent(String game, String period,
                                                  String from, String to) {
        ChatInputInteractionEvent event = mock(ChatInputInteractionEvent.class);

        replyMono    = mock(InteractionApplicationCommandCallbackReplyMono.class);
        withEphemeral = mock(InteractionApplicationCommandCallbackReplyMono.class);
        withContent  = mock(InteractionApplicationCommandCallbackReplyMono.class);
        InteractionApplicationCommandCallbackReplyMono withComponents =
                mock(InteractionApplicationCommandCallbackReplyMono.class);

        doReturn(replyMono).when(event).reply();
        doReturn(withEphemeral).when(replyMono).withEphemeral(true);
        doReturn(withContent).when(withEphemeral).withContent(any(String.class));
        doReturn(Mono.empty()).when(withContent).then();
        // For large-period confirmation prompt: .withContent(...).withComponents(buttons).then(...)
        doReturn(withComponents).when(withContent)
                .withComponents(any(discord4j.core.object.component.LayoutComponent[].class));
        // Pass-through so the .then(Mono.fromRunnable(scheduleTimeout)) actually executes
        org.mockito.stubbing.Answer<Mono<?>> ptComp = inv -> inv.getArgument(0);
        org.mockito.Mockito.doAnswer(ptComp).when(withComponents).then(any(Mono.class));

        wireStringOption(event, BotText.CMD_STATS_GAME_OPTION, game);
        wireStringOption(event, BotText.CMD_STATS_PERIOD_OPTION, period);
        wireOptionalStringOption(event, BotText.CMD_STATS_FROM_OPTION, from);
        wireOptionalStringOption(event, BotText.CMD_STATS_TO_OPTION, to);

        return event;
    }

    /** Builds an event for small-period (week) where deferReply is used. */
    private ChatInputInteractionEvent buildEventForSmallPeriod(String game, String period) {
        ChatInputInteractionEvent event = mock(ChatInputInteractionEvent.class);

        replyMono    = mock(InteractionApplicationCommandCallbackReplyMono.class);
        withEphemeral = mock(InteractionApplicationCommandCallbackReplyMono.class);
        withContent  = mock(InteractionApplicationCommandCallbackReplyMono.class);

        doReturn(replyMono).when(event).reply();
        doReturn(withEphemeral).when(replyMono).withEphemeral(true);
        doReturn(withContent).when(withEphemeral).withContent(any(String.class));
        doReturn(Mono.empty()).when(withContent).then();

        // deferReply chain
        var deferMono = mock(discord4j.core.spec.InteractionCallbackSpecDeferReplyMono.class);
        doReturn(deferMono).when(event).deferReply();
        doReturn(deferMono).when(deferMono).withEphemeral(any(Boolean.class));
        // .then(Mono<T>) — return the inner mono so the chain continues with computed content
        org.mockito.stubbing.Answer<Mono<?>> passThrough = inv -> inv.getArgument(0);
        org.mockito.Mockito.doAnswer(passThrough).when(deferMono).then(any(Mono.class));

        // editReply chain
        InteractionReplyEditMono editMono = mock(InteractionReplyEditMono.class);
        doReturn(editMono).when(event).editReply(any(String.class));
        doReturn(Mono.empty()).when(editMono).then();

        wireStringOption(event, BotText.CMD_STATS_GAME_OPTION, game);
        wireStringOption(event, BotText.CMD_STATS_PERIOD_OPTION, period);
        wireOptionalStringOption(event, BotText.CMD_STATS_FROM_OPTION, null);
        wireOptionalStringOption(event, BotText.CMD_STATS_TO_OPTION, null);

        return event;
    }

    private static void wireStringOption(ChatInputInteractionEvent event, String name, String value) {
        ApplicationCommandInteractionOptionValue v = mock(ApplicationCommandInteractionOptionValue.class);
        doReturn(value).when(v).asString();
        ApplicationCommandInteractionOption opt = mock(ApplicationCommandInteractionOption.class);
        doReturn(Optional.of(v)).when(opt).getValue();
        doReturn(Optional.of(opt)).when(event).getOption(name);
    }

    private static void wireOptionalStringOption(ChatInputInteractionEvent event,
                                                   String name, String value) {
        if (value == null) {
            doReturn(Optional.empty()).when(event).getOption(name);
        } else {
            wireStringOption(event, name, value);
        }
    }
}

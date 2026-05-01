package com.wandocorp.nytscorebot.listener;

import com.wandocorp.nytscorebot.BotText;
import com.wandocorp.nytscorebot.service.stats.GameTypeFilter;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Tests static helper methods in {@link StatsConfirmationButtonListener}
 * and the {@link StatsConfirmationTracker}.
 */
class StatsConfirmationButtonListenerTest {

    private static final LocalDate TODAY   = LocalDate.of(2026, 4, 15);
    private static final LocalDate ANCHOR  = LocalDate.of(2025, 1, 1);

    // ── resolveWindow ─────────────────────────────────────────────────────────

    @Test
    void resolveWindowWeek() {
        LocalDate[] w = StatsConfirmationButtonListener.resolveWindow("week", TODAY, ANCHOR);
        assertThat(w[0]).isEqualTo(TODAY.minusDays(7));
        assertThat(w[1]).isEqualTo(TODAY.minusDays(1));
    }

    @Test
    void resolveWindowMonth() {
        LocalDate[] w = StatsConfirmationButtonListener.resolveWindow("month", TODAY, ANCHOR);
        assertThat(w[0]).isEqualTo(TODAY.minusMonths(1).plusDays(1));
        assertThat(w[1]).isEqualTo(TODAY.minusDays(1));
    }

    @Test
    void resolveWindowYear() {
        LocalDate[] w = StatsConfirmationButtonListener.resolveWindow("year", TODAY, ANCHOR);
        assertThat(w[0]).isEqualTo(TODAY.minusYears(1).plusDays(1));
        assertThat(w[1]).isEqualTo(TODAY.minusDays(1));
    }

    @Test
    void resolveWindowAllTimeUsesAnchor() {
        LocalDate[] w = StatsConfirmationButtonListener.resolveWindow("all-time", TODAY, ANCHOR);
        assertThat(w[0]).isEqualTo(ANCHOR);
        assertThat(w[1]).isEqualTo(TODAY.minusDays(1));
    }

    @Test
    void resolveWindowAllTimeFallbackWhenAnchorNull() {
        LocalDate[] w = StatsConfirmationButtonListener.resolveWindow("all-time", TODAY, null);
        assertThat(w[0]).isEqualTo(TODAY.minusYears(10));
        assertThat(w[1]).isEqualTo(TODAY.minusDays(1));
    }

    @Test
    void resolveWindowUnknownPeriodDefaultsToWeek() {
        LocalDate[] w = StatsConfirmationButtonListener.resolveWindow("unknown", TODAY, ANCHOR);
        assertThat(w[0]).isEqualTo(TODAY.minusDays(7));
        assertThat(w[1]).isEqualTo(TODAY.minusDays(1));
    }

    // ── gameFilter ────────────────────────────────────────────────────────────

    @Test
    void gameFilterMini() {
        assertThat(StatsConfirmationButtonListener.gameFilter("mini")).isEqualTo(GameTypeFilter.MINI);
    }

    @Test
    void gameFilterMidi() {
        assertThat(StatsConfirmationButtonListener.gameFilter("midi")).isEqualTo(GameTypeFilter.MIDI);
    }

    @Test
    void gameFilterMain() {
        assertThat(StatsConfirmationButtonListener.gameFilter("main")).isEqualTo(GameTypeFilter.MAIN);
    }

    @Test
    void gameFilterAllAndDefault() {
        assertThat(StatsConfirmationButtonListener.gameFilter("all")).isEqualTo(GameTypeFilter.ALL);
        assertThat(StatsConfirmationButtonListener.gameFilter("unknown")).isEqualTo(GameTypeFilter.ALL);
    }

    // ── periodLabel ───────────────────────────────────────────────────────────

    @Test
    void periodLabelMappings() {
        assertThat(StatsConfirmationButtonListener.periodLabel("week"))
                .isEqualTo(BotText.STATS_PERIOD_LABEL_WEEKLY);
        assertThat(StatsConfirmationButtonListener.periodLabel("month"))
                .isEqualTo(BotText.STATS_PERIOD_LABEL_MONTHLY);
        assertThat(StatsConfirmationButtonListener.periodLabel("year"))
                .isEqualTo(BotText.STATS_PERIOD_LABEL_YEARLY);
        assertThat(StatsConfirmationButtonListener.periodLabel("all-time"))
                .isEqualTo(BotText.STATS_PERIOD_LABEL_ALL_TIME);
        assertThat(StatsConfirmationButtonListener.periodLabel("custom"))
                .isEqualTo(BotText.STATS_PERIOD_LABEL_CUSTOM);
    }

    // ── StatsConfirmationTracker ──────────────────────────────────────────────

    @Test
    void trackerRegisterAndRemove() {
        StatsConfirmationTracker tracker = new StatsConfirmationTracker();
        reactor.core.Disposable disposable = mock(reactor.core.Disposable.class);

        tracker.register("key1", disposable);
        assertThat(tracker.remove("key1")).isPresent().contains(disposable);
    }

    @Test
    void trackerRemoveAbsentKeyReturnsEmpty() {
        StatsConfirmationTracker tracker = new StatsConfirmationTracker();
        assertThat(tracker.remove("no-such-key")).isEmpty();
    }

    @Test
    void trackerRemoveIsIdempotent() {
        StatsConfirmationTracker tracker = new StatsConfirmationTracker();
        reactor.core.Disposable disposable = mock(reactor.core.Disposable.class);
        tracker.register("key", disposable);

        tracker.remove("key"); // first remove
        assertThat(tracker.remove("key")).isEmpty(); // second remove is empty
    }

    // ── handleButton ──────────────────────────────────────────────────────────

    @Test
    void handleButtonExpiredPromptReplies() {
        StatsConfirmationTracker tracker = new StatsConfirmationTracker();
        StatsConfirmationButtonListener listener = newListener(tracker);

        var event = mock(discord4j.core.event.domain.interaction.ButtonInteractionEvent.class);
        org.mockito.Mockito.doReturn("stats-confirm:all:year::").when(event).getCustomId();
        var reply = mock(discord4j.core.spec.InteractionApplicationCommandCallbackReplyMono.class);
        org.mockito.Mockito.doReturn(reply).when(event).reply();
        org.mockito.Mockito.doReturn(reply).when(reply).withEphemeral(true);
        org.mockito.Mockito.doReturn(reply).when(reply).withContent(BotText.STATS_CONFIRM_EXPIRED);
        org.mockito.Mockito.doReturn(reactor.core.publisher.Mono.empty()).when(reply).then();

        listener.handleButton(event).subscribe();

        org.mockito.Mockito.verify(reply).withContent(BotText.STATS_CONFIRM_EXPIRED);
    }

    @Test
    void handleButtonCancelDisposesTimerAndEdits() {
        StatsConfirmationTracker tracker = new StatsConfirmationTracker();
        reactor.core.Disposable timer = mock(reactor.core.Disposable.class);
        tracker.register("all:year::", timer);

        StatsConfirmationButtonListener listener = newListener(tracker);

        var event = mock(discord4j.core.event.domain.interaction.ButtonInteractionEvent.class);
        org.mockito.Mockito.doReturn("stats-cancel:all:year::").when(event).getCustomId();

        var deferMono = mock(discord4j.core.spec.InteractionCallbackSpecDeferEditMono.class);
        org.mockito.Mockito.doReturn(deferMono).when(event).deferEdit();
        var editMono = mock(discord4j.core.spec.InteractionReplyEditMono.class);
        org.mockito.Mockito.doReturn(editMono).when(event).editReply(BotText.STATS_CONFIRM_CANCELLED);
        org.mockito.Mockito.doReturn(reactor.core.publisher.Mono.empty()).when(deferMono).then(any(reactor.core.publisher.Mono.class));
        org.mockito.Mockito.doReturn(reactor.core.publisher.Mono.empty()).when(editMono).then();

        listener.handleButton(event).subscribe();

        org.mockito.Mockito.verify(timer).dispose();
        org.mockito.Mockito.verify(event).editReply(BotText.STATS_CONFIRM_CANCELLED);
        assertThat(tracker.remove("all:year::")).isEmpty();
    }

    @Test
    void handleButtonConfirmDisposesTimerAndComputes() {
        StatsConfirmationTracker tracker = new StatsConfirmationTracker();
        reactor.core.Disposable timer = mock(reactor.core.Disposable.class);
        tracker.register("all:year::", timer);

        com.wandocorp.nytscorebot.service.stats.CrosswordStatsService stats =
                mock(com.wandocorp.nytscorebot.service.stats.CrosswordStatsService.class);
        com.wandocorp.nytscorebot.service.stats.CrosswordStatsReportBuilder builder =
                mock(com.wandocorp.nytscorebot.service.stats.CrosswordStatsReportBuilder.class);
        com.wandocorp.nytscorebot.discord.StatsChannelService channel =
                mock(com.wandocorp.nytscorebot.discord.StatsChannelService.class);
        com.wandocorp.nytscorebot.config.DiscordChannelProperties channelProps =
                new com.wandocorp.nytscorebot.config.DiscordChannelProperties();
        channelProps.setStatsChannelId("ch-1");
        com.wandocorp.nytscorebot.service.PuzzleCalendar cal =
                mock(com.wandocorp.nytscorebot.service.PuzzleCalendar.class);
        com.wandocorp.nytscorebot.config.StatsProperties props =
                mock(com.wandocorp.nytscorebot.config.StatsProperties.class);

        org.mockito.Mockito.when(cal.today()).thenReturn(TODAY);
        org.mockito.Mockito.when(props.getAnchorDate()).thenReturn(ANCHOR);
        var fakeReport = mock(com.wandocorp.nytscorebot.service.stats.CrosswordStatsReport.class);
        org.mockito.Mockito.when(stats.compute(any(), any(), any())).thenReturn(fakeReport);
        org.mockito.Mockito.when(builder.render(any(), any())).thenReturn("rendered");
        org.mockito.Mockito.when(channel.post(any())).thenReturn(reactor.core.publisher.Mono.empty());

        StatsConfirmationButtonListener listener = new StatsConfirmationButtonListener(
                mock(discord4j.core.GatewayDiscordClient.class), tracker, stats, builder, channel,
                channelProps, cal, props,
                mock(com.wandocorp.nytscorebot.repository.ScoreboardRepository.class));

        var event = mock(discord4j.core.event.domain.interaction.ButtonInteractionEvent.class);
        org.mockito.Mockito.doReturn("stats-confirm:all:year::").when(event).getCustomId();

        var deferMono = mock(discord4j.core.spec.InteractionCallbackSpecDeferEditMono.class);
        org.mockito.Mockito.doReturn(deferMono).when(event).deferEdit();
        var editComputing = mock(discord4j.core.spec.InteractionReplyEditMono.class);
        var editPosted = mock(discord4j.core.spec.InteractionReplyEditMono.class);
        org.mockito.Mockito.doReturn(editComputing).when(event).editReply(BotText.STATS_CONFIRM_COMPUTING);
        org.mockito.Mockito.doReturn(editPosted).when(event).editReply(org.mockito.ArgumentMatchers.contains("ch-1"));
        // Pass-through chain so all mono steps actually run
        org.mockito.stubbing.Answer<reactor.core.publisher.Mono<?>> pt = inv -> inv.getArgument(0);
        org.mockito.Mockito.doAnswer(pt).when(deferMono).then(any(reactor.core.publisher.Mono.class));
        org.mockito.Mockito.doAnswer(pt).when(editComputing).then(any(reactor.core.publisher.Mono.class));
        org.mockito.Mockito.doReturn(reactor.core.publisher.Mono.empty()).when(editPosted).then();

        listener.handleButton(event).subscribe();

        org.mockito.Mockito.verify(timer).dispose();
        org.mockito.Mockito.verify(stats).compute(any(), any(), any());
        org.mockito.Mockito.verify(channel).post("rendered");
    }

    @Test
    void handleButtonConfirmCustomPeriodParsesDates() {
        StatsConfirmationTracker tracker = new StatsConfirmationTracker();
        reactor.core.Disposable timer = mock(reactor.core.Disposable.class);
        String key = "mini:custom:2025-02-01:2025-02-15";
        tracker.register(key, timer);

        com.wandocorp.nytscorebot.service.stats.CrosswordStatsService stats =
                mock(com.wandocorp.nytscorebot.service.stats.CrosswordStatsService.class);
        com.wandocorp.nytscorebot.service.stats.CrosswordStatsReportBuilder builder =
                mock(com.wandocorp.nytscorebot.service.stats.CrosswordStatsReportBuilder.class);
        com.wandocorp.nytscorebot.discord.StatsChannelService channel =
                mock(com.wandocorp.nytscorebot.discord.StatsChannelService.class);
        com.wandocorp.nytscorebot.config.DiscordChannelProperties channelProps =
                new com.wandocorp.nytscorebot.config.DiscordChannelProperties();
        channelProps.setStatsChannelId("ch-1");
        com.wandocorp.nytscorebot.service.PuzzleCalendar cal =
                mock(com.wandocorp.nytscorebot.service.PuzzleCalendar.class);
        com.wandocorp.nytscorebot.config.StatsProperties props =
                mock(com.wandocorp.nytscorebot.config.StatsProperties.class);
        org.mockito.Mockito.when(cal.today()).thenReturn(TODAY);

        org.mockito.Mockito.when(stats.compute(any(), any(), any()))
                .thenReturn(mock(com.wandocorp.nytscorebot.service.stats.CrosswordStatsReport.class));
        org.mockito.Mockito.when(builder.render(any(), any())).thenReturn("r");
        org.mockito.Mockito.when(channel.post(any())).thenReturn(reactor.core.publisher.Mono.empty());

        StatsConfirmationButtonListener listener = new StatsConfirmationButtonListener(
                mock(discord4j.core.GatewayDiscordClient.class), tracker, stats, builder, channel,
                channelProps, cal, props,
                mock(com.wandocorp.nytscorebot.repository.ScoreboardRepository.class));

        var event = mock(discord4j.core.event.domain.interaction.ButtonInteractionEvent.class);
        org.mockito.Mockito.doReturn("stats-confirm:" + key).when(event).getCustomId();

        var deferMono = mock(discord4j.core.spec.InteractionCallbackSpecDeferEditMono.class);
        org.mockito.Mockito.doReturn(deferMono).when(event).deferEdit();
        var editComputing = mock(discord4j.core.spec.InteractionReplyEditMono.class);
        var editPosted = mock(discord4j.core.spec.InteractionReplyEditMono.class);
        org.mockito.Mockito.doReturn(editComputing).when(event).editReply(BotText.STATS_CONFIRM_COMPUTING);
        org.mockito.Mockito.doReturn(editPosted).when(event).editReply(org.mockito.ArgumentMatchers.contains("ch-1"));
        org.mockito.stubbing.Answer<reactor.core.publisher.Mono<?>> pt = inv -> inv.getArgument(0);
        org.mockito.Mockito.doAnswer(pt).when(deferMono).then(any(reactor.core.publisher.Mono.class));
        org.mockito.Mockito.doAnswer(pt).when(editComputing).then(any(reactor.core.publisher.Mono.class));
        org.mockito.Mockito.doReturn(reactor.core.publisher.Mono.empty()).when(editPosted).then();

        listener.handleButton(event).subscribe();

        org.mockito.Mockito.verify(stats).compute(eq(GameTypeFilter.MINI),
                eq(LocalDate.of(2025, 2, 1)), eq(LocalDate.of(2025, 2, 15)));
    }

    private static StatsConfirmationButtonListener newListener(StatsConfirmationTracker tracker) {
        return new StatsConfirmationButtonListener(
                mock(discord4j.core.GatewayDiscordClient.class), tracker,
                mock(com.wandocorp.nytscorebot.service.stats.CrosswordStatsService.class),
                mock(com.wandocorp.nytscorebot.service.stats.CrosswordStatsReportBuilder.class),
                mock(com.wandocorp.nytscorebot.discord.StatsChannelService.class),
                new com.wandocorp.nytscorebot.config.DiscordChannelProperties(),
                mock(com.wandocorp.nytscorebot.service.PuzzleCalendar.class),
                mock(com.wandocorp.nytscorebot.config.StatsProperties.class),
                mock(com.wandocorp.nytscorebot.repository.ScoreboardRepository.class));
    }

    // ── Static helper ─────────────────────────────────────────────────────────

    private static <T> T mock(Class<T> cls) {
        return org.mockito.Mockito.mock(cls);
    }
}

package com.wandocorp.nytscorebot.listener;

import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties.ChannelConfig;
import com.wandocorp.nytscorebot.discord.ResultsChannelService;
import com.wandocorp.nytscorebot.discord.StatusChannelService;
import com.wandocorp.nytscorebot.model.GameType;
import com.wandocorp.nytscorebot.model.MainCrosswordResult;
import com.wandocorp.nytscorebot.model.MiniCrosswordResult;
import com.wandocorp.nytscorebot.model.WordleResult;
import com.wandocorp.nytscorebot.parser.GameResultParser;
import com.wandocorp.nytscorebot.service.PbUpdateOutcome;
import com.wandocorp.nytscorebot.service.SaveOutcome;
import com.wandocorp.nytscorebot.service.SaveResult;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.MessageCreateMono;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageListenerTest {

    private static final String CHANNEL_ID       = "111";
    private static final String OTHER_CHANNEL_ID = "222";
    private static final String USER_ID          = "999";
    private static final String NAME             = "Alice";
    private static final Snowflake CHANNEL_SNOWFLAKE = Snowflake.of(CHANNEL_ID);

    private GameResultParser parser;
    private ScoreboardService scoreboardService;
    private StatusChannelService statusChannelService;
    private ResultsChannelService resultsChannelService;
    private MessageListener listener;

    @BeforeEach
    void setUp() {
        parser            = mock(GameResultParser.class);
        scoreboardService = mock(ScoreboardService.class);
        statusChannelService = mock(StatusChannelService.class);
        resultsChannelService = mock(ResultsChannelService.class);

        DiscordChannelProperties props = new DiscordChannelProperties();
        ChannelConfig ch = new ChannelConfig();
        ch.setId(CHANNEL_ID);
        ch.setName(NAME);
        ch.setUserId(USER_ID);
        props.setChannels(List.of(ch));

        listener = new MessageListener(null, props, parser, scoreboardService, statusChannelService, resultsChannelService);
    }

    // ── isChannelMonitored ────────────────────────────────────────────────────

    @Test
    void monitoredChannelReturnsTrue() {
        assertThat(listener.isChannelMonitored(CHANNEL_SNOWFLAKE)).isTrue();
    }

    @Test
    void unknownChannelReturnsFalse() {
        assertThat(listener.isChannelMonitored(Snowflake.of("0"))).isFalse();
    }

    // ── isFromConfiguredUser ──────────────────────────────────────────────────

    @Test
    void matchingUserReturnsTrue() {
        assertThat(listener.isFromConfiguredUser(CHANNEL_SNOWFLAKE, USER_ID)).isTrue();
    }

    @Test
    void wrongUserReturnsFalse() {
        assertThat(listener.isFromConfiguredUser(CHANNEL_SNOWFLAKE, "wrong")).isFalse();
    }

    @Test
    void emptyAuthorReturnsFalse() {
        assertThat(listener.isFromConfiguredUser(CHANNEL_SNOWFLAKE, null)).isFalse();
    }

    // ── replyForOutcome ───────────────────────────────────────────────────────

    @Test
    void savedOutcomeReturnsEmptyMono() {
        MessageChannel channel = mock(MessageChannel.class);
        listener.replyForOutcome(Mono.just(channel), SaveOutcome.SAVED).block();
        verify(channel, never()).createMessage(anyString());
    }

    /**
     * Verifies that createMessage is called with the expected text.
     * We subscribe without blocking: Mono.just(channel) emits synchronously so
     * createMessage() is invoked before the subscriber receives the result.
     */
    private void assertReplyMessage(SaveOutcome outcome, String expectedFragment) {
        MessageChannel channel = mock(MessageChannel.class);
        MessageCreateMono messageCreateMono = mock(MessageCreateMono.class);
        when(channel.createMessage(anyString())).thenReturn(messageCreateMono);
        // subscribe (non-blocking) — createMessage() is called synchronously
        listener.replyForOutcome(Mono.just(channel), outcome).subscribe();
        verify(channel).createMessage(contains(expectedFragment));
    }

    @Test
    void wrongPuzzleNumberSendsWarning() {
        assertReplyMessage(SaveOutcome.WRONG_PUZZLE_NUMBER, "puzzle number");
    }

    @Test
    void alreadySubmittedSendsInfo() {
        assertReplyMessage(SaveOutcome.ALREADY_SUBMITTED, "already submitted");
    }

    @Test
    void alreadyFinishedSendsLockedMessage() {
        assertReplyMessage(SaveOutcome.ALREADY_FINISHED, "finished");
    }

    // ── processMessage ────────────────────────────────────────────────────────

    @Test
    void recognisedMessageSavesResult() {
        WordleResult result = new WordleResult("raw", NAME, null, 100, 3, true, false);
        when(parser.parse(anyString(), eq(NAME))).thenReturn(Optional.of(result));
        when(scoreboardService.saveResult(any(), any(), any(), any())).thenReturn(SaveResult.of(SaveOutcome.SAVED));

        listener.processMessage(CHANNEL_SNOWFLAKE, "Wordle 100 3/6", Mono.empty()).block();

        verify(scoreboardService).saveResult(eq(CHANNEL_ID), eq(NAME), eq(USER_ID), eq(result));
    }

    @Test
    void savedOutcomeCallsRefresh() {
        WordleResult result = new WordleResult("raw", NAME, null, 100, 3, true, false);
        when(parser.parse(anyString(), eq(NAME))).thenReturn(Optional.of(result));
        when(scoreboardService.saveResult(any(), any(), any(), any())).thenReturn(SaveResult.of(SaveOutcome.SAVED));

        listener.processMessage(CHANNEL_SNOWFLAKE, "Wordle 100 3/6", Mono.empty()).block();

        verify(statusChannelService).refresh(anyString());
    }

    @Test
    void savedOutcomeCallsRefreshGameWhenResultsAlreadyPosted() {
        WordleResult result = new WordleResult("raw", NAME, null, 100, 3, true, false);
        when(parser.parse(anyString(), eq(NAME))).thenReturn(Optional.of(result));
        when(scoreboardService.saveResult(any(), any(), any(), any())).thenReturn(SaveResult.of(SaveOutcome.SAVED));
        when(resultsChannelService.hasPostedResults()).thenReturn(true);

        listener.processMessage(CHANNEL_SNOWFLAKE, "Wordle 100 3/6", Mono.empty()).block();

        verify(resultsChannelService).refreshGame("Wordle");
        verify(resultsChannelService, never()).refresh();
    }

    @Test
    void savedOutcomeCallsRefreshAllWhenResultsNotYetPosted() {
        WordleResult result = new WordleResult("raw", NAME, null, 100, 3, true, false);
        when(parser.parse(anyString(), eq(NAME))).thenReturn(Optional.of(result));
        when(scoreboardService.saveResult(any(), any(), any(), any())).thenReturn(SaveResult.of(SaveOutcome.SAVED));
        when(resultsChannelService.hasPostedResults()).thenReturn(false);

        listener.processMessage(CHANNEL_SNOWFLAKE, "Wordle 100 3/6", Mono.empty()).block();

        verify(resultsChannelService).refresh();
        verify(resultsChannelService, never()).refreshGame(any());
    }

    @Test
    void nonSavedOutcomeDoesNotCallRefresh() {
        WordleResult result = new WordleResult("raw", NAME, null, 100, 3, true, false);
        when(parser.parse(anyString(), eq(NAME))).thenReturn(Optional.of(result));
        when(scoreboardService.saveResult(any(), any(), any(), any())).thenReturn(SaveResult.of(SaveOutcome.ALREADY_SUBMITTED));

        listener.processMessage(CHANNEL_SNOWFLAKE, "Wordle 100 3/6", Mono.empty()).block();

        verify(statusChannelService, never()).refresh(anyString());
    }

    @Test
    void unrecognisedMessageDoesNotSave() {
        when(parser.parse(anyString(), eq(NAME))).thenReturn(Optional.empty());
        listener.processMessage(CHANNEL_SNOWFLAKE, "hello world", Mono.empty()).block();
        verify(scoreboardService, never()).saveResult(any(), any(), any(), any());
    }

    // ── PB-break announcements ────────────────────────────────────────────────

    @Test
    void noChangeOutcomePostsNoExtraMessage() {
        MiniCrosswordResult result = new MiniCrosswordResult("raw", NAME, null, "1:00", 60,
                java.time.LocalDate.of(2026, 5, 9));
        when(parser.parse(anyString(), eq(NAME))).thenReturn(Optional.of(result));
        when(scoreboardService.saveResult(any(), any(), any(), any()))
                .thenReturn(SaveResult.saved(PbUpdateOutcome.NO_CHANGE));
        MessageChannel channel = mock(MessageChannel.class);

        listener.processMessage(CHANNEL_SNOWFLAKE, "...", Mono.just(channel)).subscribe();

        verify(channel, never()).createMessage(anyString());
    }

    @Test
    void newPbWithPriorPostsAnnouncementWithWasSegment() {
        MiniCrosswordResult result = new MiniCrosswordResult("raw", NAME, null, "0:50", 50,
                java.time.LocalDate.of(2026, 5, 9));
        PbUpdateOutcome.NewPb npb = new PbUpdateOutcome.NewPb(60, 50, GameType.MINI_CROSSWORD, Optional.empty());
        when(parser.parse(anyString(), eq(NAME))).thenReturn(Optional.of(result));
        when(scoreboardService.saveResult(any(), any(), any(), any()))
                .thenReturn(SaveResult.saved(npb));
        MessageChannel channel = mock(MessageChannel.class);
        MessageCreateMono mcm = mock(MessageCreateMono.class);
        when(mcm.then()).thenReturn(Mono.empty());
        when(channel.createMessage(anyString())).thenReturn(mcm);

        listener.processMessage(CHANNEL_SNOWFLAKE, "...", Mono.just(channel)).subscribe();

        verify(channel).createMessage(contains("New PB"));
        verify(channel).createMessage(contains("0:50"));
        verify(channel).createMessage(contains("(was 1:00)"));
        verify(channel).createMessage(contains(NAME));
        verify(channel).createMessage(contains("Mini"));
    }

    @Test
    void firstEverNewPbOmitsPriorSegment() {
        MiniCrosswordResult result = new MiniCrosswordResult("raw", NAME, null, "1:00", 60,
                java.time.LocalDate.of(2026, 5, 9));
        PbUpdateOutcome.NewPb npb = new PbUpdateOutcome.NewPb(null, 60, GameType.MINI_CROSSWORD, Optional.empty());
        when(parser.parse(anyString(), eq(NAME))).thenReturn(Optional.of(result));
        when(scoreboardService.saveResult(any(), any(), any(), any()))
                .thenReturn(SaveResult.saved(npb));
        MessageChannel channel = mock(MessageChannel.class);
        MessageCreateMono mcm = mock(MessageCreateMono.class);
        when(mcm.then()).thenReturn(Mono.empty());
        when(channel.createMessage(anyString())).thenReturn(mcm);

        listener.processMessage(CHANNEL_SNOWFLAKE, "...", Mono.just(channel)).subscribe();

        org.mockito.ArgumentCaptor<String> cap = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(channel).createMessage(cap.capture());
        assertThat(cap.getValue()).doesNotContain("(was");
        assertThat(cap.getValue()).contains("New PB");
    }

    @Test
    void mainNewPbIncludesDayOfWeekToken() {
        MainCrosswordResult result = new MainCrosswordResult("raw", NAME, null, "10:00", 600,
                java.time.LocalDate.of(2026, 5, 9));
        PbUpdateOutcome.NewPb npb = new PbUpdateOutcome.NewPb(700, 600, GameType.MAIN_CROSSWORD,
                Optional.of(java.time.DayOfWeek.SATURDAY));
        when(parser.parse(anyString(), eq(NAME))).thenReturn(Optional.of(result));
        when(scoreboardService.saveResult(any(), any(), any(), any()))
                .thenReturn(SaveResult.saved(npb));
        MessageChannel channel = mock(MessageChannel.class);
        MessageCreateMono mcm = mock(MessageCreateMono.class);
        when(mcm.then()).thenReturn(Mono.empty());
        when(channel.createMessage(anyString())).thenReturn(mcm);

        listener.processMessage(CHANNEL_SNOWFLAKE, "...", Mono.just(channel)).subscribe();

        verify(channel).createMessage(contains("Saturday"));
        verify(channel).createMessage(contains("Main"));
        verify(channel).createMessage(contains("10:00"));
        verify(channel).createMessage(contains("(was 11:40)"));
    }

    // ── listenToEvents filter chain ───────────────────────────────────────────

    private MessageCreateEvent buildEvent(String channelId, String authorId) {
        User discordUser = mock(User.class);
        doReturn(Snowflake.of(authorId)).when(discordUser).getId();

        Message message = mock(Message.class);
        doReturn(Snowflake.of(channelId)).when(message).getChannelId();
        doReturn(Optional.of(discordUser)).when(message).getAuthor();
        doReturn("Wordle 100 3/6").when(message).getContent();
        doReturn(Mono.<MessageChannel>empty()).when(message).getChannel();

        MessageCreateEvent event = mock(MessageCreateEvent.class);
        doReturn(message).when(event).getMessage();
        return event;
    }

    @Test
    void monitoredChannelAndCorrectUserProcessesEvent() {
        WordleResult result = new WordleResult("raw", NAME, null, 100, 3, true, false);
        when(parser.parse(anyString(), eq(NAME))).thenReturn(Optional.of(result));
        when(scoreboardService.saveResult(any(), any(), any(), any())).thenReturn(SaveResult.of(SaveOutcome.SAVED));

        listener.listenToEvents(Flux.just(buildEvent(CHANNEL_ID, USER_ID)));

        verify(scoreboardService).saveResult(eq(CHANNEL_ID), eq(NAME), eq(USER_ID), eq(result));
    }

    @Test
    void wrongUserIdIsFiltered() {
        listener.listenToEvents(Flux.just(buildEvent(CHANNEL_ID, "888")));
        verify(parser, never()).parse(any(), any());
    }

    @Test
    void unmonitoredChannelIsFiltered() {
        listener.listenToEvents(Flux.just(buildEvent(OTHER_CHANNEL_ID, USER_ID)));
        verify(parser, never()).parse(any(), any());
    }
}

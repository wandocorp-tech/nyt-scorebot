package com.wandocorp.nytscorebot;

import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties.ChannelConfig;
import com.wandocorp.nytscorebot.model.WordleResult;
import com.wandocorp.nytscorebot.parser.GameResultParser;
import com.wandocorp.nytscorebot.service.SaveOutcome;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageListenerTest {

    private static final String CHANNEL_ID       = "111";
    private static final String OTHER_CHANNEL_ID = "222";
    private static final String USER_ID          = "999";
    private static final String NAME             = "Alice";
    private static final Snowflake CHANNEL_SNOWFLAKE = Snowflake.of(CHANNEL_ID);

    private GameResultParser parser;
    private ScoreboardService scoreboardService;
    private MessageListener listener;

    @BeforeEach
    void setUp() {
        parser            = mock(GameResultParser.class);
        scoreboardService = mock(ScoreboardService.class);

        DiscordChannelProperties props = new DiscordChannelProperties();
        ChannelConfig ch = new ChannelConfig();
        ch.setId(CHANNEL_ID);
        ch.setName(NAME);
        ch.setUserId(USER_ID);
        props.setChannels(List.of(ch));

        // GatewayDiscordClient is only used in subscribe() → client.on(...)
        // Tests call listenToEvents(Flux) or the extracted methods directly,
        // so we never need a real client here.
        listener = new MessageListener(null, props, parser, scoreboardService);
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
        assertThat(listener.isFromConfiguredUser(CHANNEL_SNOWFLAKE, Optional.of(USER_ID))).isTrue();
    }

    @Test
    void wrongUserReturnsFalse() {
        assertThat(listener.isFromConfiguredUser(CHANNEL_SNOWFLAKE, Optional.of("wrong"))).isFalse();
    }

    @Test
    void emptyAuthorReturnsFalse() {
        assertThat(listener.isFromConfiguredUser(CHANNEL_SNOWFLAKE, Optional.empty())).isFalse();
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
     * The unstubbed mock returns null, which Reactor's flatMap converts to an
     * onError (discarded); the important thing is the invocation was recorded.
     */
    private void assertReplyMessage(SaveOutcome outcome, String expectedFragment) {
        MessageChannel channel = mock(MessageChannel.class);
        // subscribe (non-blocking) — createMessage() is called synchronously
        listener.replyForOutcome(Mono.just(channel), outcome).subscribe();
        verify(channel).createMessage(contains(expectedFragment));
    }

    @Test
    void wrongPuzzleNumberSendsWarning() {
        assertReplyMessage(SaveOutcome.WRONG_PUZZLE_NUMBER, "puzzle number");
    }

    @Test
    void wrongDateSendsWarning() {
        assertReplyMessage(SaveOutcome.WRONG_DATE, "crossword date");
    }

    @Test
    void alreadySubmittedSendsInfo() {
        assertReplyMessage(SaveOutcome.ALREADY_SUBMITTED, "already submitted");
    }

    // ── processMessage ────────────────────────────────────────────────────────

    @Test
    void recognisedMessageSavesResult() {
        WordleResult result = new WordleResult("raw", NAME, null, 100, 3, true, false);
        when(parser.parse(anyString(), eq(NAME))).thenReturn(Optional.of(result));
        when(scoreboardService.saveResult(any(), any(), any(), any())).thenReturn(SaveOutcome.SAVED);

        listener.processMessage(CHANNEL_SNOWFLAKE, "Wordle 100 3/6", Mono.empty()).block();

        verify(scoreboardService).saveResult(eq(CHANNEL_ID), eq(NAME), eq(USER_ID), eq(result));
    }

    @Test
    void unrecognisedMessageDoesNotSave() {
        when(parser.parse(anyString(), eq(NAME))).thenReturn(Optional.empty());
        listener.processMessage(CHANNEL_SNOWFLAKE, "hello world", Mono.empty()).block();
        verify(scoreboardService, never()).saveResult(any(), any(), any(), any());
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
        when(scoreboardService.saveResult(any(), any(), any(), any())).thenReturn(SaveOutcome.SAVED);

        AtomicBoolean completed = new AtomicBoolean();
        listener.listenToEvents(
                Flux.just(buildEvent(CHANNEL_ID, USER_ID))
                    .doOnComplete(() -> completed.set(true)));

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

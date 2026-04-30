package com.wandocorp.nytscorebot.discord;

import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties.ChannelConfig;
import com.wandocorp.nytscorebot.service.ScoreboardService;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.MessageCreateMono;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class StatusChannelServiceTest {

    private static final String NAME_ALICE = "Alice";
    private static final String NAME_BOB = "Bob";
    private static final String STATUS_CHANNEL_ID = "777";
    private static final String CTX = "Alice submitted Wordle";

    private GatewayDiscordClient client;
    private ScoreboardService scoreboardService;
    private DiscordChannelProperties channelProperties;
    private MessageSlotWriter slotWriter;
    private StatusChannelService service;

    @BeforeEach
    void setUp() {
        client = mock(GatewayDiscordClient.class);
        scoreboardService = mock(ScoreboardService.class);
        when(scoreboardService.getTodayScoreboards()).thenReturn(List.of());
        slotWriter = mock(MessageSlotWriter.class);
        when(slotWriter.editOrPost(any(Snowflake.class), any(), anyString()))
                .thenAnswer(inv -> {
                    Snowflake existing = inv.getArgument(1);
                    return Mono.just(existing != null ? existing : Snowflake.of("9999"));
                });

        channelProperties = new DiscordChannelProperties();
        ChannelConfig alice = new ChannelConfig();
        alice.setId("111"); alice.setName(NAME_ALICE); alice.setUserId("aaa");
        ChannelConfig bob = new ChannelConfig();
        bob.setId("222"); bob.setName(NAME_BOB); bob.setUserId("bbb");
        channelProperties.setChannels(List.of(alice, bob));
        channelProperties.setStatusChannelId(STATUS_CHANNEL_ID);

        service = new StatusChannelService(client, channelProperties, scoreboardService, slotWriter,
                Schedulers.immediate());
    }

    private MessageChannel stubChannel() {
        MessageChannel channel = mock(MessageChannel.class);
        when(client.getChannelById(any(Snowflake.class))).thenReturn(Mono.just(channel));
        return channel;
    }

    private Message stubCreatedMessage(MessageChannel channel, Snowflake messageId) {
        Message msg = mock(Message.class);
        when(msg.getId()).thenReturn(messageId);
        when(msg.delete()).thenReturn(Mono.empty());
        MessageCreateMono mcMono = mock(MessageCreateMono.class);
        doAnswer(inv -> {
            CoreSubscriber<? super Message> sub = inv.getArgument(0);
            Mono.just(msg).subscribe(sub);
            return null;
        }).when(mcMono).subscribe(any(CoreSubscriber.class));
        when(channel.createMessage(anyString())).thenReturn(mcMono);
        return msg;
    }

    // ── refresh() no-op ───────────────────────────────────────────────────────

    @Test
    void refreshNoOpWhenStatusChannelIdIsNull() {
        channelProperties.setStatusChannelId(null);
        service.refresh(CTX);
        verifyNoInteractions(slotWriter);
        verifyNoInteractions(client);
    }

    @Test
    void refreshNoOpWhenStatusChannelIdIsBlank() {
        channelProperties.setStatusChannelId("  ");
        service.refresh(CTX);
        verifyNoInteractions(slotWriter);
        verifyNoInteractions(client);
    }

    // ── persistent board ──────────────────────────────────────────────────────

    @Test
    void firstRefreshPostsPersistentBoardWithNullExistingId() {
        stubChannel();
        stubCreatedMessage(stubChannel(), Snowflake.of("12345")); // ephemeral notification

        service.refresh(CTX);

        verify(slotWriter).editOrPost(eq(Snowflake.of(STATUS_CHANNEL_ID)), isNull(), anyString());
        assertThat(service.getLastMessageId()).isEqualTo(Snowflake.of("9999"));
    }

    @Test
    void subsequentRefreshEditsPersistentBoardInPlace() {
        Snowflake existing = Snowflake.of("11111");
        service.setLastMessageId(existing);
        stubCreatedMessage(stubChannel(), Snowflake.of("12345"));

        service.refresh(CTX);

        verify(slotWriter).editOrPost(eq(Snowflake.of(STATUS_CHANNEL_ID)), eq(existing), anyString());
        assertThat(service.getLastMessageId()).isEqualTo(existing);
    }

    @Test
    void refreshUpdatesLastMessageIdWhenHelperReturnsNewId() {
        Snowflake existing = Snowflake.of("11111");
        Snowflake replaced = Snowflake.of("22222");
        service.setLastMessageId(existing);
        when(slotWriter.editOrPost(any(Snowflake.class), eq(existing), anyString()))
                .thenReturn(Mono.just(replaced));
        stubCreatedMessage(stubChannel(), Snowflake.of("12345"));

        service.refresh(CTX);

        assertThat(service.getLastMessageId()).isEqualTo(replaced);
    }

    // ── ephemeral notification ────────────────────────────────────────────────

    @Test
    void refreshPostsAndDeletesEphemeralNotification() {
        MessageChannel channel = stubChannel();
        Message ephemeral = stubCreatedMessage(channel, Snowflake.of("12345"));

        service.refresh(CTX);

        verify(channel).createMessage(CTX);
        verify(ephemeral).delete();
    }

    @Test
    void refreshSkipsEphemeralWhenContextIsNull() {
        MessageChannel channel = stubChannel();
        service.refresh(null);
        verify(channel, never()).createMessage(anyString());
        verify(client, never()).getChannelById(any(Snowflake.class));
    }

    @Test
    void refreshSkipsEphemeralWhenContextIsBlank() {
        MessageChannel channel = stubChannel();
        service.refresh("   ");
        verify(channel, never()).createMessage(anyString());
        verify(client, never()).getChannelById(any(Snowflake.class));
    }

    // ── resetForNewDay ────────────────────────────────────────────────────────

    @Test
    void resetForNewDayEditsExistingBoardToEmptyState() {
        Snowflake existing = Snowflake.of("33333");
        service.setLastMessageId(existing);

        service.resetForNewDay();

        verify(slotWriter).editOrPost(eq(Snowflake.of(STATUS_CHANNEL_ID)), eq(existing), anyString());
    }

    @Test
    void resetForNewDayNoOpWhenNoBoardYet() {
        service.resetForNewDay();
        verifyNoInteractions(slotWriter);
    }

    @Test
    void resetForNewDayNoOpWhenStatusChannelIdMissing() {
        channelProperties.setStatusChannelId(null);
        service.setLastMessageId(Snowflake.of("33333"));
        service.resetForNewDay();
        verifyNoInteractions(slotWriter);
    }

    // ── buildStatusTable ──────────────────────────────────────────────────────

    @Test
    void buildStatusTableDelegatestoBuilder() {
        when(scoreboardService.getTodayScoreboards()).thenReturn(List.of());
        String table = service.buildStatusTable();
        assertThat(table).startsWith("```");
        verify(scoreboardService, times(1)).getTodayScoreboards();
    }
}

package com.wandocorp.nytscorebot.service;

import com.wandocorp.nytscorebot.config.DiscordChannelProperties;
import com.wandocorp.nytscorebot.config.DiscordChannelProperties.ChannelConfig;
import discord4j.core.GatewayDiscordClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
class StatusChannelServiceTest {

    private static final String NAME_ALICE = "Alice";
    private static final String NAME_BOB = "Bob";

    private ScoreboardService scoreboardService;
    private DiscordChannelProperties channelProperties;
    private StatusChannelService service;

    @BeforeEach
    void setUp() {
        scoreboardService = mock(ScoreboardService.class);
        channelProperties = new DiscordChannelProperties();

        ChannelConfig alice = new ChannelConfig();
        alice.setId("111");
        alice.setName(NAME_ALICE);
        alice.setUserId("aaa");

        ChannelConfig bob = new ChannelConfig();
        bob.setId("222");
        bob.setName(NAME_BOB);
        bob.setUserId("bbb");

        channelProperties.setChannels(List.of(alice, bob));

        service = new StatusChannelService(null, channelProperties, scoreboardService);
    }

    // ── buildStatusTable ──────────────────────────────────────────────────────

    @Test
    void buildStatusTableDelegatestoBuilder() {
        when(scoreboardService.getTodayScoreboards()).thenReturn(List.of());
        when(scoreboardService.getTodayScoreboards()).thenReturn(List.of());
        String table = service.buildStatusTable("test context");
        // Table wrapping and structure validation moved to StatusMessageBuilderTest
        verify(scoreboardService, times(1)).getTodayScoreboards();
    }

    // ── refresh() no-op ───────────────────────────────────────────────────────

    @Test
    void refreshNoOpWhenStatusChannelIdIsNull() {
        channelProperties.setStatusChannelId(null);
        GatewayDiscordClient client = mock(GatewayDiscordClient.class);
        StatusChannelService svc = new StatusChannelService(client, channelProperties, scoreboardService);
        svc.refresh("test context");
        verifyNoInteractions(client);
    }

    @Test
    void refreshNoOpWhenStatusChannelIdIsBlank() {
        channelProperties.setStatusChannelId("  ");
        GatewayDiscordClient client = mock(GatewayDiscordClient.class);
        StatusChannelService svc = new StatusChannelService(client, channelProperties, scoreboardService);
        svc.refresh("test context");
        verifyNoInteractions(client);
    }
}

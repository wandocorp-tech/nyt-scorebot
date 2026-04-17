package com.wandocorp.nytscorebot.config;

import discord4j.core.GatewayDiscordClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscordHealthIndicatorTest {

    @Test
    void healthIsUpWhenGatewayConnected() {
        GatewayDiscordClient client = mock(GatewayDiscordClient.class);
        when(client.onDisconnect()).thenReturn(Mono.never());

        DiscordHealthIndicator indicator = new DiscordHealthIndicator(client);

        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("gateway", "connected");
    }

    @Test
    void healthIsDownAfterDisconnect() {
        GatewayDiscordClient client = mock(GatewayDiscordClient.class);
        when(client.onDisconnect()).thenReturn(Mono.empty());

        DiscordHealthIndicator indicator = new DiscordHealthIndicator(client);

        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("gateway", "disconnected");
    }
}

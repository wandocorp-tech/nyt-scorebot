package com.wandocorp.nytscorebot.config;

import discord4j.core.GatewayDiscordClient;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DiscordLifecycleManagerTest {

    @Test
    void startSetsRunning() {
        GatewayDiscordClient client = mock(GatewayDiscordClient.class);
        DiscordLifecycleManager manager = new DiscordLifecycleManager(client);

        assertThat(manager.isRunning()).isFalse();
        manager.start();
        assertThat(manager.isRunning()).isTrue();
    }

    @Test
    void stopCallsLogoutAndClearsRunning() {
        GatewayDiscordClient client = mock(GatewayDiscordClient.class);
        when(client.logout()).thenReturn(Mono.empty());

        DiscordLifecycleManager manager = new DiscordLifecycleManager(client);
        manager.start();

        Runnable callback = mock(Runnable.class);
        manager.stop(callback);

        verify(client).logout();
        verify(callback).run();
        assertThat(manager.isRunning()).isFalse();
    }

    @Test
    void stopInvokesCallbackEvenOnError() {
        GatewayDiscordClient client = mock(GatewayDiscordClient.class);
        when(client.logout()).thenReturn(Mono.error(new RuntimeException("test error")));

        DiscordLifecycleManager manager = new DiscordLifecycleManager(client);
        manager.start();

        Runnable callback = mock(Runnable.class);
        manager.stop(callback);

        verify(callback).run();
        assertThat(manager.isRunning()).isFalse();
    }

    @Test
    void stopWhenNotRunningInvokesCallbackWithoutLogout() {
        GatewayDiscordClient client = mock(GatewayDiscordClient.class);
        DiscordLifecycleManager manager = new DiscordLifecycleManager(client);

        Runnable callback = mock(Runnable.class);
        manager.stop(callback);

        verify(client, never()).logout();
        verify(callback).run();
    }

    @Test
    void parameterlessStopDelegatesToOverload() {
        GatewayDiscordClient client = mock(GatewayDiscordClient.class);
        when(client.logout()).thenReturn(Mono.empty());

        DiscordLifecycleManager manager = new DiscordLifecycleManager(client);
        manager.start();
        manager.stop();

        verify(client).logout();
        assertThat(manager.isRunning()).isFalse();
    }

    @Test
    void phaseIsMaxValue() {
        GatewayDiscordClient client = mock(GatewayDiscordClient.class);
        DiscordLifecycleManager manager = new DiscordLifecycleManager(client);

        assertThat(manager.getPhase()).isEqualTo(Integer.MAX_VALUE);
    }
}

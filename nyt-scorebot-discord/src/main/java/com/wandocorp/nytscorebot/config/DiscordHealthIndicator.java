package com.wandocorp.nytscorebot.config;

import discord4j.core.GatewayDiscordClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Readiness health indicator that reports the Discord gateway connection status.
 * Reports UP when the gateway is connected, DOWN after disconnect.
 */
@Slf4j
@Component
public class DiscordHealthIndicator implements HealthIndicator {

    private final AtomicBoolean connected = new AtomicBoolean(false);

    public DiscordHealthIndicator(GatewayDiscordClient client) {
        // Client is already connected at bean creation time (.login().block() in DiscordConfig)
        connected.set(true);
        client.onDisconnect()
                .doFinally(signal -> connected.set(false))
                .subscribe(
                        v -> {},
                        error -> log.error("Error tracking Discord disconnect", error));
    }

    @Override
    public Health health() {
        if (connected.get()) {
            return Health.up()
                    .withDetail("gateway", "connected")
                    .build();
        }
        return Health.down()
                .withDetail("gateway", "disconnected")
                .build();
    }
}

package com.wandocorp.nytscorebot.config;

import discord4j.core.GatewayDiscordClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Manages graceful shutdown of the Discord gateway connection.
 * Uses {@link SmartLifecycle} so Spring invokes {@code stop()} during context close,
 * cleanly logging out from the Discord gateway before the application exits.
 */
@Slf4j
@Component
public class DiscordLifecycleManager implements SmartLifecycle {

    private static final Duration LOGOUT_TIMEOUT = Duration.ofSeconds(10);

    private final GatewayDiscordClient client;
    private volatile boolean running = false;

    public DiscordLifecycleManager(GatewayDiscordClient client) {
        this.client = client;
    }

    @Override
    public void start() {
        running = true;
        log.info("Discord lifecycle manager started");
    }

    @Override
    public void stop() {
        stop(() -> {});
    }

    @Override
    public void stop(Runnable callback) {
        if (!running) {
            callback.run();
            return;
        }
        log.info("Initiating graceful Discord shutdown...");
        try {
            client.logout().block(LOGOUT_TIMEOUT);
            log.info("Discord client disconnected gracefully");
        } catch (Exception e) {
            log.error("Error during Discord logout", e);
        } finally {
            running = false;
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // Stop last among SmartLifecycle beans — event listeners depend on the connection
        return Integer.MAX_VALUE;
    }
}

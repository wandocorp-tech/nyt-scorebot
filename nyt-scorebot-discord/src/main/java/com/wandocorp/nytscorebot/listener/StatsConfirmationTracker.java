package com.wandocorp.nytscorebot.listener;

import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks pending auto-cancel timers for the {@code /stats} confirmation prompt.
 *
 * <p>When {@link StatsCommandHandler} posts a confirmation prompt it registers a
 * {@link Disposable} timer keyed by {@code <game>:<period>:<from>:<to>}. The
 * {@link StatsConfirmationButtonListener} removes and disposes the entry when a
 * button is clicked within the 15-second window; the timer's own callback removes
 * the entry when it fires.
 */
@Component
public class StatsConfirmationTracker {

    private final ConcurrentHashMap<String, Disposable> pending = new ConcurrentHashMap<>();

    public void register(String key, Disposable timer) {
        pending.put(key, timer);
    }

    /**
     * Remove and return the pending timer for {@code key}, or empty if no entry exists
     * (e.g. the timer already fired and removed itself first).
     */
    public Optional<Disposable> remove(String key) {
        return Optional.ofNullable(pending.remove(key));
    }
}

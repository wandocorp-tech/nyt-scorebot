package com.wandocorp.nytscorebot.service.scoreboard;

import com.wandocorp.nytscorebot.model.GameType;

/**
 * Renderer-facing lookup for per-player prior-clean averages and current PBs.
 * Implementations bind to the rendered date so callers don't pass a date through the
 * renderer signature. The {@code EMPTY} singleton returns {@link CrosswordPbStats#EMPTY}
 * for every key — used when no PB data is available (e.g. tests or a fresh DB).
 */
@FunctionalInterface
public interface CrosswordPbLookup {
    CrosswordPbStats lookup(String playerName, GameType gameType);

    CrosswordPbLookup EMPTY = (playerName, gameType) -> CrosswordPbStats.EMPTY;
}

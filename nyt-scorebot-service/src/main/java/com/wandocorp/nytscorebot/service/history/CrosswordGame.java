package com.wandocorp.nytscorebot.service.history;

import com.wandocorp.nytscorebot.model.GameType;

/**
 * The three crossword games that participate in the avg/pb stats table.
 * Persisted as the {@code game_type} column in {@code crossword_history_stats}.
 */
public enum CrosswordGame {
    MINI(GameType.MINI_CROSSWORD),
    MIDI(GameType.MIDI_CROSSWORD),
    MAIN(GameType.MAIN_CROSSWORD);

    private final GameType gameType;

    CrosswordGame(GameType gameType) {
        this.gameType = gameType;
    }

    public GameType gameType() {
        return gameType;
    }

    public static CrosswordGame fromGameType(GameType type) {
        return switch (type) {
            case MINI_CROSSWORD -> MINI;
            case MIDI_CROSSWORD -> MIDI;
            case MAIN_CROSSWORD -> MAIN;
            default -> throw new IllegalArgumentException("Not a crossword game: " + type);
        };
    }
}

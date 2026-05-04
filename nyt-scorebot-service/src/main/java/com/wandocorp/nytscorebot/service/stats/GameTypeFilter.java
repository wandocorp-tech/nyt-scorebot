package com.wandocorp.nytscorebot.service.stats;

import com.wandocorp.nytscorebot.model.GameType;

import java.util.Set;

/** Filter for which crossword game(s) to include in a stats report. */
public enum GameTypeFilter {
    MINI(Set.of(GameType.MINI_CROSSWORD)),
    MIDI(Set.of(GameType.MIDI_CROSSWORD)),
    MAIN(Set.of(GameType.MAIN_CROSSWORD)),
    ALL(Set.of(GameType.MINI_CROSSWORD, GameType.MIDI_CROSSWORD, GameType.MAIN_CROSSWORD));

    private final Set<GameType> gameTypes;

    GameTypeFilter(Set<GameType> gameTypes) {
        this.gameTypes = gameTypes;
    }

    public Set<GameType> gameTypes() {
        return gameTypes;
    }
}

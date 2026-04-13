package com.wandocorp.nytscorebot.model;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum GameType {
    WORDLE("Wordle"),
    CONNECTIONS("Connections"),
    STRANDS("Strands"),
    MINI_CROSSWORD("Mini"),
    MIDI_CROSSWORD("Midi"),
    MAIN_CROSSWORD("Main");

    private static final Map<String, GameType> BY_LABEL =
            Stream.of(values()).collect(Collectors.toMap(GameType::label, Function.identity()));

    private final String label;

    GameType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Lookup by display label (e.g. "Wordle" → WORDLE). Returns null if not found. */
    public static GameType fromLabel(String label) {
        return BY_LABEL.get(label);
    }
}

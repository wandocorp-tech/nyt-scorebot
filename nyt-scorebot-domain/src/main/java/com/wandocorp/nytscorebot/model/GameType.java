package com.wandocorp.nytscorebot.model;

public enum GameType {
    WORDLE("Wordle"),
    CONNECTIONS("Connections"),
    STRANDS("Strands"),
    MINI_CROSSWORD("Mini"),
    MIDI_CROSSWORD("Midi"),
    MAIN_CROSSWORD("Main");

    private final String label;

    GameType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

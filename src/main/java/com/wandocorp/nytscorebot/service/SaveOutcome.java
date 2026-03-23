package com.wandocorp.nytscorebot.service;

/**
 * Outcome of an attempt to save a game result to the scoreboard.
 */
public enum SaveOutcome {

    /** Result was successfully persisted. */
    SAVED,

    /** The puzzle number does not match today's expected puzzle (Wordle, Connections, Strands). */
    WRONG_PUZZLE_NUMBER,

    /** The crossword date does not match today's date. */
    WRONG_DATE,

    /** A result for this game type has already been submitted today. */
    ALREADY_SUBMITTED
}

package com.wandocorp.nytscorebot.service;

/**
 * Outcome of an attempt to mark a scoreboard as finished via /finished.
 */
public enum MarkFinishedOutcome {

    /** Scoreboard was successfully marked as finished. */
    MARKED_FINISHED,

    /** Scoreboard was already marked as finished; no change made. */
    ALREADY_FINISHED,

    /** The user has no scoreboard for the requested date. */
    NO_SCOREBOARD_FOR_DATE,

    /** The Discord user ID does not match any tracked user. */
    USER_NOT_FOUND
}

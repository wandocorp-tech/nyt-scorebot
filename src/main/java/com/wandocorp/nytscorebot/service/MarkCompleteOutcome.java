package com.wandocorp.nytscorebot.service;

/**
 * Outcome of an attempt to mark a scoreboard as complete via /finished.
 */
public enum MarkCompleteOutcome {

    /** Scoreboard was successfully marked complete. */
    MARKED_COMPLETE,

    /** Scoreboard was already marked complete; no change made. */
    ALREADY_COMPLETE,

    /** The user has no scoreboard for the requested date. */
    NO_SCOREBOARD_FOR_DATE,

    /** The Discord user ID does not match any tracked user. */
    USER_NOT_FOUND
}

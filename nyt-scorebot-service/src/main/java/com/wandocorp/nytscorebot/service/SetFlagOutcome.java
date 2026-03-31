package com.wandocorp.nytscorebot.service;

/**
 * Outcome of an attempt to set or toggle a crossword flag via slash command.
 */
public enum SetFlagOutcome {

    /** Flag was successfully set (or value was stored). */
    FLAG_SET,

    /** Flag was successfully cleared (toggled off or value set to 0). */
    FLAG_CLEARED,

    /** The user has no main crossword result for the requested date. */
    NO_MAIN_CROSSWORD,

    /** The user has no scoreboard record for the requested date. */
    NO_SCOREBOARD_FOR_DATE,

    /** The Discord user ID does not match any tracked user. */
    USER_NOT_FOUND,

    /** The provided value is invalid (e.g., negative lookups). */
    INVALID_VALUE
}

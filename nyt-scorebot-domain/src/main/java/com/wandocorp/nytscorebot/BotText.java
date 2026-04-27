package com.wandocorp.nytscorebot;

public final class BotText {

    /** Max width of a scoreboard line — fits two player columns in Discord monospace font. */
    public static final int MAX_LINE_WIDTH = 33;

    // ── Emojis ───────────────────────────────────────────────────────────────
    public static final String SUBMITTED = "✅";
    public static final String PENDING   = "⏳";
    public static final String WARNING   = "⚠️";
    public static final String INFO      = "ℹ️";

    public static final String FINISHED = "🟢";
    public static final String NOT_FINISHED = "⚫️";

    // ── Game labels ──────────────────────────────────────────────────────────
    public static final String GAME_LABEL_WORDLE      = "Wordle";
    public static final String GAME_LABEL_CONNECTIONS = "Connections";
    public static final String GAME_LABEL_STRANDS     = "Strands";
    public static final String GAME_LABEL_MINI        = "Mini";
    public static final String GAME_LABEL_MIDI        = "Midi";
    public static final String GAME_LABEL_MAIN        = "Main";

    // ── Slash commands ───────────────────────────────────────────────────────
    public static final String CMD_FINISHED                = "finished";
    public static final String CMD_FINISHED_DESCRIPTION    = "Mark your scorecard as complete for today";
    public static final String CMD_DUO                     = "duo";
    public static final String CMD_DUO_DESCRIPTION         = "Toggle duo flag on today's main crossword";
    public static final String CMD_LOOKUPS                 = "lookups";
    public static final String CMD_LOOKUPS_DESCRIPTION     = "Set lookup count on today's main crossword";
    public static final String CMD_LOOKUPS_OPTION          = "count";
    public static final String CMD_LOOKUPS_OPTION_DESC     = "Number of lookups used (0 to clear)";
    public static final String CMD_CHECK                   = "check";
    public static final String CMD_CHECK_DESCRIPTION       = "Toggle check flag on today's main crossword";
    public static final String CMD_STREAK                  = "streak";
    public static final String CMD_STREAK_DESCRIPTION      = "Set your current streak for a game";
    public static final String CMD_STREAK_GAME_OPTION      = "game";
    public static final String CMD_STREAK_GAME_OPTION_DESC = "The game type";
    public static final String CMD_STREAK_VALUE_OPTION     = "streak";
    public static final String CMD_STREAK_VALUE_OPTION_DESC = "Current streak value (0 or higher)";

    // ── Reply messages ────────────────────────────────────────────────────────
    public static final String MSG_WRONG_PUZZLE_NUMBER =
            WARNING + " That doesn't look like today's puzzle number. Result was not saved.";
    public static final String MSG_ALREADY_SUBMITTED =
            INFO + " You've already submitted this game type today. Result was not saved.";
    public static final String MSG_MARKED_FINISHED =
            SUBMITTED + " Your scoreboard for today has been marked as finished!";
    public static final String MSG_ALREADY_FINISHED =
            "Your scoreboard was already marked as finished for today.";
    public static final String MSG_NO_SCOREBOARD_TODAY =
            "You haven't submitted any results for today yet.";
    public static final String MSG_USER_NOT_FOUND =
            "You are not a tracked user in this bot.";
    public static final String MSG_INVALID_VALUE        = WARNING + " Value must be non-negative.";

    // ── Flag reply messages ───────────────────────────────────────────────────
    public static final String MSG_DUO_SET              = SUBMITTED + " Duo marked";
    public static final String MSG_DUO_CLEARED          = "❌ Duo cleared";
    public static final String MSG_LOOKUPS_SET          = SUBMITTED + " Lookups set to %d";
    public static final String MSG_LOOKUPS_CLEARED      = "❌ Lookups cleared";
    public static final String MSG_CHECK_SET            = SUBMITTED + " Check marked";
    public static final String MSG_CHECK_CLEARED        = "❌ Check cleared";
    public static final String MSG_NO_MAIN_CROSSWORD    = "You haven't submitted a main crossword result today.";
    public static final String MSG_STREAK_SET            = "✅ %s streak set to %d";

    // ── Status table ──────────────────────────────────────────────────────────
    public static final String STATUS_GAME_COL_HEADER  = "Game";
    public static final String STATUS_FOOTER_DONE_LABEL = "Finished";
    public static final String STATUS_CODE_BLOCK_OPEN  = "```\n";
    public static final String STATUS_CODE_BLOCK_CLOSE = "```";
    public static final String STATUS_COL_SEPARATOR    = "-+-";

    // ── Status header context messages (configurable for tweaking) ────────────
    // %s placeholders: %1$s = player name, %2$s = game label
    public static final String STATUS_CONTEXT_GAME_SUBMITTED    = "%s submitted %s";
    public static final String STATUS_CONTEXT_PLAYER_FINISHED   = "%s is done for today";
    public static final String STATUS_CONTEXT_FLAG_UPDATED      = "%s updated %s";

    // ── Scoreboard result / comparison messages ───────────────────────────────
    public static final String SCOREBOARD_TIE            = "🤝 Tie!";
    public static final String SCOREBOARD_NUKE           = "☢️ Nuke!";
    public static final String SCOREBOARD_WIN_WITH_DIFF  = "🏆 %s wins! (%s)";
    public static final String SCOREBOARD_WIN_NO_DIFF    = "🏆 %s wins!";
    public static final String SCOREBOARD_WAITING        = "⏳ %s hasn't submitted";
    public static final String SCOREBOARD_STREAK         = "%d";

    // ── Crossword scoreboard labels ───────────────────────────────────────────
    public static final String SCOREBOARD_MINI_HEADER   = "Mini - %s";
    public static final String SCOREBOARD_MIDI_HEADER   = "Midi - %s";
    public static final String SCOREBOARD_MAIN_HEADER   = "Main - %s";

    // ── Crossword flag indicators ─────────────────────────────────────────────
    public static final String FLAG_DUO     = "👫";
    public static final String FLAG_LOOKUPS = "🔍×%d";
    public static final String FLAG_CHECK   = "✓";

    private BotText() {}
}

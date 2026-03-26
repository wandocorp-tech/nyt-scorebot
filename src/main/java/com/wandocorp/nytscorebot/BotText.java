package com.wandocorp.nytscorebot;

public final class BotText {

    public static final int MAX_LINE_WIDTH = 35;

    // ── Emojis ───────────────────────────────────────────────────────────────
    public static final String SUBMITTED = "🟢";
    public static final String PENDING   = "⚪️";
    public static final String WARNING   = "⚠️";
    public static final String INFO      = "ℹ️";

    public static final String GREEN  = "🟢";
    public static final String YELLOW = "🟡";
    public static final String ORANGE = "🟠";
    public static final String RED    = "🔴";

    public static final String CHECK_MARK = "✅";
    public static final String CROSS_MARK = "❌";

    // ── Reply messages ────────────────────────────────────────────────────────
    public static final String MSG_WRONG_PUZZLE_NUMBER =
            WARNING + " That doesn't look like today's puzzle number. Result was not saved.";
    public static final String MSG_WRONG_DATE =
            WARNING + " That crossword date doesn't match today. Result was not saved.";
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

    // ── Slash command ─────────────────────────────────────────────────────────
    public static final String CMD_FINISHED             = "finished";
    public static final String CMD_FINISHED_DESCRIPTION = "Mark your scorecard as complete for today";

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

    public static final String GAME_LABEL_WORDLE      = "Wordle";
    public static final String GAME_LABEL_CONNECTIONS = "Connections";
    public static final String GAME_LABEL_STRANDS     = "strands";
    public static final String GAME_LABEL_MINI        = "mini";
    public static final String GAME_LABEL_MIDI        = "midi";
    public static final String GAME_LABEL_MAIN        = "main";

    private BotText() {}
}

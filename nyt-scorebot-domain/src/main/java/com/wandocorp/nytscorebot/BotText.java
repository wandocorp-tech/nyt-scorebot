package com.wandocorp.nytscorebot;

public final class BotText {

    /** Max width of a scoreboard line — fits two player columns in Discord monospace font. */
    public static final int MAX_LINE_WIDTH = 33;

    /** Width of a single-player scoreboard separator — half the full board width. */
    public static final int SINGLE_PLAYER_LINE_WIDTH = (MAX_LINE_WIDTH + 1) / 2;

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
    public static final String MSG_FINISHED_LOCKED =
            INFO + " Your scorecard is marked as finished. No more submissions until tomorrow.";
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
    public static final String STATUS_CONTEXT_FLAG_UPDATED      = "%s set a flag";

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

    // ── Crossword win streak summary ──────────────────────────────────────────
    public static final String WIN_STREAK_TITLE          = "Win Streaks";
    public static final String WIN_STREAK_GAME_COL_LABEL = "Game";
    public static final String WIN_STREAK_FIRE_EMOJI     = "🔥";
    /** Threshold at or above which the fire emoji decorates the streak value. */
    public static final int    WIN_STREAK_FIRE_THRESHOLD = 3;

    // ── /stats slash command ──────────────────────────────────────────────────
    public static final String CMD_STATS                        = "stats";
    public static final String CMD_STATS_DESCRIPTION            = "View crossword stats for a period";
    public static final String CMD_STATS_GAME_OPTION            = "game";
    public static final String CMD_STATS_GAME_OPTION_DESC       = "Which crossword game(s) to include";
    public static final String CMD_STATS_PERIOD_OPTION          = "period";
    public static final String CMD_STATS_PERIOD_OPTION_DESC     = "Time period for the report";
    public static final String CMD_STATS_FROM_OPTION            = "from";
    public static final String CMD_STATS_FROM_OPTION_DESC       = "Start date (YYYY-MM-DD, required for custom period)";
    public static final String CMD_STATS_TO_OPTION              = "to";
    public static final String CMD_STATS_TO_OPTION_DESC         = "End date (YYYY-MM-DD, required for custom period)";

    // Period choice values
    public static final String STATS_PERIOD_WEEK     = "week";
    public static final String STATS_PERIOD_MONTH    = "month";
    public static final String STATS_PERIOD_YEAR     = "year";
    public static final String STATS_PERIOD_ALL_TIME = "all-time";
    public static final String STATS_PERIOD_CUSTOM   = "custom";

    // Game choice values
    public static final String STATS_GAME_MINI = "mini";
    public static final String STATS_GAME_MIDI = "midi";
    public static final String STATS_GAME_MAIN = "main";
    public static final String STATS_GAME_ALL  = "all";

    // Period labels used in the report header
    public static final String STATS_PERIOD_LABEL_WEEKLY   = "Weekly";
    public static final String STATS_PERIOD_LABEL_MONTHLY  = "Monthly";
    public static final String STATS_PERIOD_LABEL_YEARLY   = "Yearly";
    public static final String STATS_PERIOD_LABEL_ALL_TIME = "All-Time";
    public static final String STATS_PERIOD_LABEL_CUSTOM   = "Custom";

    // Report header / section headers
    public static final String STATS_REPORT_HEADER         = "📊 %s Crossword Recap — %s";
    public static final String STATS_GAME_SECTION_HEADER   = "── %s ──────────────────────────────────";
    public static final String STATS_DOW_SECTION_HEADER    = "── %s Day-of-Week Avg ──────────────────";
    public static final String STATS_EMPTY_PERIOD          = "No crossword results in this window.";

    // Day-of-week labels
    public static final String STATS_DOW_MON = "Mon";
    public static final String STATS_DOW_TUE = "Tue";
    public static final String STATS_DOW_WED = "Wed";
    public static final String STATS_DOW_THU = "Thu";
    public static final String STATS_DOW_FRI = "Fri";
    public static final String STATS_DOW_SAT = "Sat";
    public static final String STATS_DOW_SUN = "Sun";

    // Rank medals
    public static final String STATS_RANK_1 = "🥇";
    public static final String STATS_RANK_2 = "🥈";
    public static final String STATS_RANK_3 = "🥉";

    // Confirmation prompt
    public static final String STATS_CONFIRM_PROMPT    = "⏳ This period covers a wider window. Computation may take a few seconds — proceed? *(auto-cancels in 15 s)*";
    public static final String STATS_CONFIRM_RUN       = "Run report";
    public static final String STATS_CONFIRM_CANCEL    = "Cancel";
    public static final String STATS_CONFIRM_CANCELLED = "Cancelled.";
    public static final String STATS_CONFIRM_TIMEOUT   = "Cancelled (timed out).";
    public static final String STATS_CONFIRM_EXPIRED   = "This prompt has expired. Please run `/stats` again.";
    public static final String STATS_CONFIRM_COMPUTING = "Computing report…";
    public static final String STATS_POSTED            = "✅ Report posted to <#%s>";

    // Error messages for /stats
    public static final String STATS_ERR_ANCHOR_UNSET         = "⚠️ Stats are not configured. Set `stats.anchor-date` to enable this feature.";
    public static final String STATS_ERR_CHANNEL_UNSET        = "⚠️ Stats channel is not configured. Set `discord.statsChannelId` to enable this feature.";
    public static final String STATS_ERR_CUSTOM_MISSING_DATES = "⚠️ `from` and `to` are both required for custom periods.";
    public static final String STATS_ERR_DATES_ON_NON_CUSTOM  = "⚠️ `from` and `to` should only be used with `period:custom`.";
    public static final String STATS_ERR_FROM_AFTER_TO        = "⚠️ `from` must be on or before `to`.";
    public static final String STATS_ERR_TO_IN_FUTURE         = "⚠️ `to` must not be in the future (must be on or before yesterday).";
    public static final String STATS_ERR_WINDOW_BEFORE_ANCHOR = "⚠️ The requested window is entirely before the configured anchor date (%s). No data is available for this period.";
    public static final String STATS_ERR_INVALID_DATE         = "⚠️ Invalid date format. Please use YYYY-MM-DD.";

    private BotText() {}
}
